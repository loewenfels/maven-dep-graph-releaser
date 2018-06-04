package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.gui.showInfo
import ch.loewenfels.depgraph.gui.sleep
import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

class JenkinsJobExecutor(
    private val usernameTokenRegistry: UsernameTokenRegistry
) : JobExecutor {

    override fun trigger(
        jobExecutionData: JobExecutionData,
        jobQueuedHook: (queuedItemUrl: String) -> Promise<*>,
        jobStartedHook: (buildNumber: Int) -> Promise<*>,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int,
        verbose: Boolean
    ): Promise<Pair<CrumbWithId, Int>> {
        val jobName = jobExecutionData.jobName
        val jenkinsBaseUrl = jobExecutionData.getJenkinsBaseUrl()
        val usernameToken = usernameTokenRegistry.forHostOrThrow(jenkinsBaseUrl)

        return issueCrumb(jenkinsBaseUrl, usernameToken).then { crumbWithId: CrumbWithId? ->
            triggerJob(usernameToken, crumbWithId, jobExecutionData)
                .then { response ->
                    checkStatusAndExtractQueuedItemUrl(response, jobExecutionData, usernameToken, crumbWithId)
                }.catch {
                    throw Error("Could not trigger the job $jobName", it)
                }.then { nullableQueuedItemUrl: String? ->
                    showInfoQueuedItemIfVerbose(verbose, nullableQueuedItemUrl, jobName)
                    val queuedItemUrl = if (nullableQueuedItemUrl != null) "${nullableQueuedItemUrl}api/xml/" else jobExecutionData.jobBaseUrl
                    jobQueuedHook(queuedItemUrl).then {
                        extractBuildNumber(nullableQueuedItemUrl, usernameToken, crumbWithId, jobExecutionData)
                    }.then { it }
                }.then { buildNumber: Int ->
                    if (verbose) {
                        showInfo(
                            "$jobName started with build number $buildNumber, wait for completion...",
                            2000
                        )
                    }
                    jobStartedHook(buildNumber).then {
                        pollJobForCompletion(
                            usernameToken,
                            crumbWithId,
                            jobExecutionData.jobBaseUrl,
                            buildNumber,
                            pollEverySecond,
                            maxWaitingTimeForCompletenessInSeconds
                        )
                    }.then { result -> buildNumber to result }
                }.then { (buildNumber, result) ->
                    check(result == SUCCESS) {
                        "$jobName failed, job did not end with status $SUCCESS but $result." +
                            "\nVisit ${jobExecutionData.jobBaseUrl}$buildNumber for further information"
                    }
                    crumbWithId to buildNumber
                }
        }.unsafeCast<Promise<Pair<CrumbWithId, Int>>>()
    }

    private fun checkStatusAndExtractQueuedItemUrl(
        response: Response,
        jobExecutionData: JobExecutionData,
        usernameAndApiToken: UsernameAndApiToken,
        crumbWithId: CrumbWithId?
    ): Promise<Promise<String?>> {
        return checkStatusOk(response).then {
            jobExecutionData.queuedItemUrlExtractor.extract(
                usernameAndApiToken, crumbWithId, response, jobExecutionData
            )
        }
    }

    private fun extractBuildNumber(
        nullableQueuedItemUrl: String?,
        usernameAndApiToken: UsernameAndApiToken,
        crumbWithId: CrumbWithId?,
        jobExecutionData: JobExecutionData
    ): Promise<Int> {
        return if (nullableQueuedItemUrl != null) {
            QueuedItemBasedBuildNumberExtractor(
                usernameAndApiToken,
                crumbWithId,
                nullableQueuedItemUrl
            ).extract()
        } else {
            BuildHistoryBasedBuildNumberExtractor(
                usernameAndApiToken,
                crumbWithId,
                jobExecutionData
            ).extract()
        }
    }

    private fun issueCrumb(
        jenkinsBaseUrl: String,
        usernameAndApiToken: UsernameAndApiToken
    ): Promise<CrumbWithId?> {
        val url = "$jenkinsBaseUrl/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)"
        val headers = createHeaderWithAuthAndCrumb(usernameAndApiToken, null)
        val init = createGetRequest(headers)
        return window.fetch(url, init)
            .then(::checkStatusOkOr404)
            .catch {
                throw Error("Cannot issue a crumb", it)
            }.then { crumbWithId: String? ->
                if (crumbWithId != null) {
                    val (id, crumb) = crumbWithId.split(':')
                    CrumbWithId(id, crumb)
                } else {
                    null
                }
            }
    }

    private fun triggerJob(usernameAndApiToken: UsernameAndApiToken, crumbWithId: CrumbWithId?, jobExecutionData: JobExecutionData): Promise<Response> {
        val headers = createHeaderWithAuthAndCrumb(usernameAndApiToken, crumbWithId)
        headers["content-type"] = "application/x-www-form-urlencoded; charset=utf-8"
        val init = createRequestInit(jobExecutionData.body, RequestVerb.POST, headers)
        return window.fetch(jobExecutionData.jobTriggerUrl, init)
    }

    private fun showInfoQueuedItemIfVerbose(
        verbose: Boolean,
        nullableQueuedItemUrl: String?,
        jobName: String
    ) {
        if (verbose) {
            if (nullableQueuedItemUrl != null) {
                showInfo(
                    "Queued $jobName successfully, wait for execution...\nQueued item URL: ${nullableQueuedItemUrl}api/xml",
                    2000
                )
            } else {
                showInfo(
                    "$jobName is probably already running (queued item could not be found), trying to fetch execution number from Job history.",
                    2000
                )
            }
        }
    }

    override fun pollAndExtract(
        usernameAndApiToken: UsernameAndApiToken,
        crumbWithId: CrumbWithId?,
        url: String,
        regex: Regex,
        errorHandler: (PollException) -> Nothing
    ): Promise<String> {
        return Poller.pollAndExtract(usernameAndApiToken, crumbWithId, url, regex, errorHandler)
    }

    private fun pollJobForCompletion(
        usernameAndApiToken: UsernameAndApiToken,
        crumbWithId: CrumbWithId?,
        jobUrl: String,
        buildNumber: Int,
        pollEverySecond: Int,
        maxWaitingTimeInSeconds: Int
    ): Promise<String> {
        return sleep(pollEverySecond * 500) {
            val pollData = Poller.PollData(
                usernameAndApiToken,
                crumbWithId,
                "$jobUrl$buildNumber/api/xml?xpath=/*/result",
                pollEverySecond,
                maxWaitingTimeInSeconds
            ) { body ->
                val matchResult =
                    resultRegex.matchEntire(body)
                if (matchResult != null) {
                    true to matchResult.groupValues[1]
                } else {
                    false to ""
                }
            }
            Poller.poll(pollData)
        }.unsafeCast<Promise<String>>()
    }

    //TODO move to api, is duplicated in RemoteJenkinsM2Releaser
    companion object {
        private val resultRegex = Regex("<result>([A-Z]+)</result>")
        private const val SUCCESS = "SUCCESS"
    }
}
