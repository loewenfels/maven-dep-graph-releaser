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
        jobQueuedHook: (queuedItemUrl: String?) -> Promise<*>,
        jobStartedHook: (buildNumber: Int) -> Promise<*>,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int,
        verbose: Boolean
    ): Promise<Pair<AuthData, Int>> {
        val jobName = jobExecutionData.jobName
        val jenkinsBaseUrl = jobExecutionData.getJenkinsBaseUrl()
        val usernameAndApiToken = usernameTokenRegistry.forHostOrThrow(jenkinsBaseUrl)

        return issueCrumb(jenkinsBaseUrl, usernameAndApiToken).then { authData: AuthData ->
            triggerJob(authData, jobExecutionData)
                .then(::checkStatusIgnoreOpaqueRedirect)
                .catch<Pair<Response, String>> {
                    throw Error(
                        "Could not trigger the job $jobName." +
                            "\nPlease visit ${jobExecutionData.jobBaseUrl} to see if it was triggered nonetheless." +
                            "\nYou can manually set the command to Succeeded if the job was triggered/executed and ended successfully."
                        , it
                    )
                }.then { (response, _) ->
                    jobExecutionData.queuedItemUrlExtractor.extract(authData, response, jobExecutionData)
                }.then { nullableQueuedItemUrl: String? ->
                    showInfoQueuedItemIfVerbose(verbose, nullableQueuedItemUrl, jobName)
                    val queuedItemUrl = getQueuedItemUrlOrNull(nullableQueuedItemUrl)
                    jobQueuedHook(queuedItemUrl).then {
                        extractBuildNumber(nullableQueuedItemUrl, authData, jobExecutionData)
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
                            authData,
                            jobExecutionData,
                            buildNumber,
                            pollEverySecond,
                            maxWaitingTimeForCompletenessInSeconds
                        )
                    }
                }
        }.unsafeCast<Promise<Pair<AuthData, Int>>>()
    }

    private fun getQueuedItemUrlOrNull(nullableQueuedItemUrl: String?)
        = if (nullableQueuedItemUrl != null) "${nullableQueuedItemUrl}api/xml/" else null

    private fun triggerJob(authData: AuthData, jobExecutionData: JobExecutionData): Promise<Response> {
        val headers = createHeaderWithAuthAndCrumb(authData)
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

    private fun extractBuildNumber(
        nullableQueuedItemUrl: String?,
        authData: AuthData,
        jobExecutionData: JobExecutionData
    ): Promise<Int> {
        return if (nullableQueuedItemUrl != null) {
            QueuedItemBasedBuildNumberExtractor(authData, nullableQueuedItemUrl).extract()
        } else {
            BuildHistoryBasedBuildNumberExtractor(authData, jobExecutionData).extract()
        }
    }

    override fun rePoll(
        jobExecutionData: JobExecutionData,
        buildNumber: Int,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int
    ): Promise<Pair<AuthData, Int>> {
        val jenkinsBaseUrl = jobExecutionData.getJenkinsBaseUrl()
        val usernameAndApiToken = usernameTokenRegistry.forHostOrThrow(jenkinsBaseUrl)
        return issueCrumb(jenkinsBaseUrl, usernameAndApiToken).then { authData ->
            pollJobForCompletion(
                authData,
                jobExecutionData,
                buildNumber,
                pollEverySecond,
                maxWaitingTimeForCompletenessInSeconds
            )
        }.then { it }
    }


    private fun pollJobForCompletion(
        authData: AuthData,
        jobExecutionData: JobExecutionData,
        buildNumber: Int,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int
    ): Promise<Pair<AuthData, Int>> {
        return sleep(pollEverySecond * 500) {
            pollAndExtract(
                authData,
                "${jobExecutionData.jobBaseUrl}$buildNumber/api/xml",
                resultRegex,
                pollEverySecond,
                maxWaitingTimeForCompletenessInSeconds,
                errorHandler = { e -> throw e }
            )
                .then { result -> buildNumber to result }
                .then { (buildNumber, result) ->
                    check(result == SUCCESS) {
                        "${jobExecutionData.jobName} failed, job did not end with status $SUCCESS but $result." +
                            "\nVisit ${jobExecutionData.jobBaseUrl}$buildNumber/$endOfConsoleUrlSuffix for further information"
                    }
                    authData to buildNumber
                }
        }.then { it }
    }

    override fun pollAndExtract(
        authData: AuthData,
        url: String,
        regex: Regex,
        pollEverySecond: Int,
        maxWaitingTimeInSeconds: Int,
        errorHandler: (PollTimeoutException) -> Nothing
    ): Promise<String> {
        return Poller.pollAndExtract(authData, url, regex, pollEverySecond, maxWaitingTimeInSeconds, errorHandler)
    }

    companion object {
        private val resultRegex = Regex("<result>([A-Z]+)</result>")
        private const val SUCCESS = "SUCCESS"
    }
}
