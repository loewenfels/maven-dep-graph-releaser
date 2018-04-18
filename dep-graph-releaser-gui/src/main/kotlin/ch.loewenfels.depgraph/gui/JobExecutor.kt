package ch.loewenfels.depgraph.gui

import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

class JobExecutor(private val jenkinsUrl: String, private val usernameToken: UsernameToken) {

    fun trigger(jobUrl: String, jobName: String, body: String): Promise<Pair<CrumbWithId, Int>> {
        return issueCrumb(jenkinsUrl).then { crumbWithId: CrumbWithId? ->
            post(crumbWithId, jobUrl, body)
                .then { response ->
                    checkStatusAndExtractQueuedItemUrl(response, jobName)
                }.catch {
                    throw Error("Could not trigger the publish job", it)
                }.then { queuedItemUrl: String ->
                    showInfo("Queued $jobName successfully, wait for execution...", 2000)
                    extractBuildNumber(crumbWithId, queuedItemUrl)
                }.then { buildNumber: Int ->
                    showInfo("$jobName started with build number $buildNumber, wait for completion...", 2000)
                    pollJobForCompletion(crumbWithId, jobUrl, buildNumber)
                        .then { result -> buildNumber to result }
                }.then { (buildNumber, result) ->
                    check(result == SUCCESS) {
                        "$jobName failed, job did not end with status $SUCCESS but $result." +
                            "\nVisit $jobUrl$buildNumber for further information"
                    }
                    crumbWithId to buildNumber
                }
        }.catch {
            showError(it)
        }
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
        val init = createRequestInit(null, RequestVerb.GET, headers)
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

    private fun post(crumbWithId: CrumbWithId?, jobUrl: String, body: String): Promise<Response> {
        val headers = createHeaderWithAuthAndCrumb(crumbWithId, usernameToken)
        headers["content-type"] = "application/x-www-form-urlencoded; charset=utf-8"
        val init = createRequestInit(body, RequestVerb.POST, headers)
        return window.fetch("${jobUrl}buildWithParameters", init)
    }

    private fun extractBuildNumber(crumbWithId: CrumbWithId?, queuedItemUrl: String): Promise<Int> {
        val xpathUrl = "${queuedItemUrl}api/xml?xpath=//executable/number"
        return pollAndExtract(crumbWithId, xpathUrl, numberRegex) { e ->
            throw IllegalStateException(
                "Could not find the build number in the returned body." +
                    "\nJob URL: $queuedItemUrl" +
                    "\nRegex used: ${numberRegex.pattern}" +
                    "\nContent: ${e.body}"
            )
        }.then { it.toInt() }
    }

    fun pollAndExtract(
        crumbWithId: CrumbWithId?,
        url: String,
        regex: Regex,
        errorHandler: (PollException) -> Nothing
    ): Promise<String> {
        return poll(crumbWithId, url, 0, { body ->
            val matchResult = regex.find(body)
            if (matchResult != null) {
                true to matchResult.groupValues[1]
            } else {
                false to null
            }
        }).catch { t -> errorHandler(t as PollException) }
    }

    private fun pollJobForCompletion(crumbWithId: CrumbWithId?, jobUrl: String, buildNumber: Int): Promise<String> {
        return poll(crumbWithId, "$jobUrl$buildNumber/api/xml?xpath=/*/result", 0, { body ->
            val matchResult = resultRegex.matchEntire(body)
            if (matchResult != null) {
                true to matchResult.groupValues[1]
            } else {
                false to ""
            }
        })
    }

    private fun <T : Any> poll(
        crumbWithId: CrumbWithId?,
        pollUrl: String,
        numberOfTries: Int,
        action: (String) -> Pair<Boolean, T?>,
        maxNumberOfTries: Int = 10,
        sleepInSeconds: Int = 2
    ): Promise<T> {
        val headers = createHeaderWithAuthAndCrumb(crumbWithId, usernameToken)
        val init = createRequestInit(null, RequestVerb.GET, headers)
        return window.fetch(pollUrl, init)
            .then(::checkStatusOk)
            .then { body ->
                val (success, result) = action(body)
                if (success) {
                    require(result != null) { "Result was null even though success flag was true" }
                    result!!
                } else {
                    if (numberOfTries >= maxNumberOfTries) {
                        throw PollException("Waited at least ${sleepInSeconds * maxNumberOfTries} seconds", body)
                    }
                    val p = sleep(sleepInSeconds * 1000) {
                        poll(crumbWithId, pollUrl, numberOfTries + 1, action)
                    }
                    // asDynamic is used because javascript resolves the result automatically on return
                    // will not result in Promise<T> but T
                    p.asDynamic() as T
                }
            }
    }

    class PollException(message: String, val body: String) : RuntimeException(message)

    companion object {
        private val numberRegex = Regex("<number>([0-9]+)</number>")
        private val resultRegex = Regex("<result>([A-Z]+)</result>")
        private const val SUCCESS = "SUCCESS"
    }
}
