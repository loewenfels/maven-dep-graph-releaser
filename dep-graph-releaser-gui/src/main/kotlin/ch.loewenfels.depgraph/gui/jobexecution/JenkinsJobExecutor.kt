package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.gui.*
import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

class JenkinsJobExecutor(
    private val jenkinsUrl: String,
    private val usernameToken: UsernameToken
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
        return issueCrumb(jenkinsUrl).then { crumbWithId: CrumbWithId? ->
            triggerJob(crumbWithId, jobExecutionData)
                .then { response ->
                    checkStatusAndExtractQueuedItemUrl(response, jobName)
                }.catch {
                    throw Error("Could not trigger the job $jobName", it)
                }.then { queuedItemUrl: String ->
                    if (verbose) {
                        showInfo(
                            "Queued $jobName successfully, wait for execution...\nQueued item URL: ${queuedItemUrl}api/xml",
                            2000
                        )
                    }
                    jobQueuedHook("${queuedItemUrl}api/xml").then {
                        extractBuildNumber(crumbWithId, queuedItemUrl)
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
                            crumbWithId, jobExecutionData.jobBaseUrl, buildNumber, pollEverySecond, maxWaitingTimeForCompletenessInSeconds
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

    private fun checkStatusAndExtractQueuedItemUrl(response: Response, jobName: String): Promise<String> {
        return checkStatusOk(response).then {
            val queuedItemUrl = response.headers.get("Location") ?: throw IllegalStateException(
                "Job $jobName queued but Location header not found in response of Jenkins." +
                    "\nHave you exposed Location with Access-Control-Expose-Headers?"
            )
            if (queuedItemUrl.endsWith("/")) queuedItemUrl else "$queuedItemUrl/"
        }
    }

    private fun issueCrumb(jenkinsUrl: String): Promise<CrumbWithId?> {
        val url = "$jenkinsUrl/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)"
        val headers = createHeaderWithAuthAndCrumb(null, usernameToken)
        val init =
            createRequestInit(null, ch.loewenfels.depgraph.gui.RequestVerb.GET, headers)
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

    private fun triggerJob(crumbWithId: CrumbWithId?, jobExecutionData: JobExecutionData): Promise<Response> {
        val headers = createHeaderWithAuthAndCrumb(crumbWithId, usernameToken)
        headers["content-type"] = "application/x-www-form-urlencoded; charset=utf-8"
        val init = createRequestInit(
            jobExecutionData.body,
            ch.loewenfels.depgraph.gui.RequestVerb.POST,
            headers
        )
        return window.fetch(jobExecutionData.jobTriggerUrl, init)
    }

    private fun extractBuildNumber(crumbWithId: CrumbWithId?, queuedItemUrl: String): Promise<Int> {
        val xpathUrl = "${queuedItemUrl}api/xml?xpath=//executable/number"
        // wait a bit, if we are too fast we run almost certainly into a 404
        return sleep(400) {
            pollAndExtract(
                crumbWithId,
                xpathUrl,
                numberRegex
            ) { e ->
                throw IllegalStateException(
                    "Could not find the build number in the returned body." +
                        "\nJob URL: $queuedItemUrl" +
                        "\nRegex used: ${numberRegex.pattern}" +
                        "\nContent: ${e.body}"
                )
            }
        }.then { it.toInt() }
    }

    override fun pollAndExtract(
        crumbWithId: CrumbWithId?,
        url: String,
        regex: Regex,
        errorHandler: (PollException) -> Nothing
    ): Promise<String> {
        return poll(crumbWithId, url, 0, 2, 20, { body ->
            val matchResult = regex.find(body)
            if (matchResult != null) {
                true to matchResult.groupValues[1]
            } else {
                false to null
            }
        }).catch { t ->
            if (t is PollException) {
                errorHandler(t)
            } else {
                throw t
            }
        }
    }

    private fun pollJobForCompletion(
        crumbWithId: CrumbWithId?,
        jobUrl: String,
        buildNumber: Int,
        pollEverySecond: Int,
        maxWaitingTimeInSeconds: Int
    ): Promise<String> {
        return sleep(pollEverySecond * 500) {
            poll(
                crumbWithId, "$jobUrl$buildNumber/api/xml?xpath=/*/result", 0, pollEverySecond, maxWaitingTimeInSeconds
            ) { body ->
                val matchResult =
                    resultRegex.matchEntire(body)
                if (matchResult != null) {
                    true to matchResult.groupValues[1]
                } else {
                    false to ""
                }
            }
        }.unsafeCast<Promise<String>>()
    }

    private fun <T : Any> poll(
        crumbWithId: CrumbWithId?,
        pollUrl: String,
        numberOfTries: Int,
        pollEverySecond: Int,
        maxWaitingTimeInSeconds: Int,
        action: (String) -> Pair<Boolean, T?>
    ): Promise<T> {
        val headers = createHeaderWithAuthAndCrumb(crumbWithId, usernameToken)
        val init =
            createRequestInit(null, ch.loewenfels.depgraph.gui.RequestVerb.GET, headers)

        val rePoll: (String) -> T = { body ->
            if (numberOfTries * pollEverySecond >= maxWaitingTimeInSeconds) {
                throw PollException(
                    "Waited at least $maxWaitingTimeInSeconds seconds",
                    body
                )
            }
            val p = sleep(pollEverySecond * 1000) {
                poll(crumbWithId, pollUrl, numberOfTries + 1, pollEverySecond, maxWaitingTimeInSeconds, action)
            }
            // unsafeCast is used because javascript resolves the result automatically on return
            // will not result in Promise<Promise<T>> but T
            p.unsafeCast<T>()
        }

        return window.fetch(pollUrl, init)
            .then(::checkStatusOk)
            .then { body: String ->
                val (success, result) = action(body)
                if (success) {
                    if (result == null) {
                        throw Error(
                            "Result was null even though success flag during polling was true, please report a bug."
                        )
                    }
                    result
                } else {
                    rePoll(body)
                }
            }.catch { t ->
                if (t is Exception) {
                    rePoll("")
                } else {
                    throw t
                }
            }
    }

    class PollException(message: String, val body: String) : RuntimeException(message)

    //TODO move to api, is duplicated in RemoteJenkinsM2Releaser
    companion object {
        private val numberRegex = Regex("<number>([0-9]+)</number>")
        private val resultRegex = Regex("<result>([A-Z]+)</result>")
        private const val SUCCESS = "SUCCESS"
    }
}
