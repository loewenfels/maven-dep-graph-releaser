package ch.loewenfels.depgraph.gui

import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

class Publisher(
    private val publishJobUrl: String,
    private val usernameToken: UsernameToken,
    private var modifiableJson: ModifiableJson
) {

    fun publish(fileName: String): Promise<Boolean> {
        val jenkinsUrl = publishJobUrl.substringBefore("/job/")
        changeCursorToProgress()
        return issueCrumb(jenkinsUrl).then { crumbWithId: CrumbWithId? ->
            post(crumbWithId, publishJobUrl, fileName)
                .then(::checkStatusOk)
                .catch {
                    throw Error("Could not trigger the publish job", it)
                }.then { _: String ->
                    showInfo("Triggered publish job successfully, wait for completion...", 2000)
                    //POST does not return anything, that's why we cannot pass a body and have to fetch it again
                    extractBuildNumber(crumbWithId, fileName, publishJobUrl)
                }.then { buildNumber: Int ->
                    pollJobForCompletion(crumbWithId, publishJobUrl, buildNumber)
                        .then { result -> buildNumber to result }
                }.then { (buildNumber, result) ->
                    checkJobResult(publishJobUrl, buildNumber, result)
                    extractResultJsonUrl(crumbWithId, publishJobUrl, buildNumber)
                }.then { (buildNumber, releaseJsonUrl) ->
                    changeUrlAndReloadOrAddHint(publishJobUrl, buildNumber, releaseJsonUrl)
                }
        }.catch {
            showError(it)
        }.finally { it: Any? ->
            changeCursorBackToNormal()
            it != null
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

    private fun post(crumbWithId: CrumbWithId?, jobUrl: String, fileName: String): Promise<Response> {
        val headers = createHeaderWithAuthAndCrumb(crumbWithId, usernameToken)
        headers["content-type"] = "application/x-www-form-urlencoded; charset=utf-8"
        val init = createRequestInit("fileName=$fileName&json=${modifiableJson.json}", RequestVerb.POST, headers)
        return window.fetch("${jobUrl}buildWithParameters", init)
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

    private fun extractBuildNumber(crumbWithId: CrumbWithId?, fileName: String, jobUrl: String): Promise<Int> {
        val buildNumberRegex = Regex(
            "<div[^>]+id=\"buildHistoryPage\"[^>]*>[\\S\\s]*?" +
                "<td[^>]+class=\"build-row-cell[^>]+>[\\S\\s]*?" +
                "<a[^>]+href=\"[^\"]*/job/[^/]+/([0-9]+)/console\"[^>]*>[\\S\\s]*?" +
                "<a[^>]+class=\"[^\"]+build-link[^>]+>#[0-9]+ $fileName[^<]*</a>[\\S\\s]*?" +
                "</td>"
        )
        return pollAndExtract(crumbWithId, jobUrl, buildNumberRegex) { e ->
            throw IllegalStateException(
                "Could not find the build number in the returned body." +
                    "\nJob URL: $jobUrl" +
                    "\nRegex used: ${buildNumberRegex.pattern}" +
                    "\nFollowing the content of the first build-row:\n" + extractFirstBuildRow(e)
            )
        }.then { it.toInt() }
    }

    private fun extractFirstBuildRow(e: PollException): String {
        val regex = Regex("<tr[^>]+class=\"[^\"]*build-row[^\"][^>]*>([\\S\\s]*?)</tr>")
        return regex.find(e.body)?.value ?: "<nothing found, 100 first chars of body instead:>\n${e.body.take(100)}"
    }

    private fun pollAndExtract(
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

    private fun checkJobResult(jobUrl: String, buildNumber: Int, result: String) {
        check(result == SUCCESS) {
            "Publishing the json failed, job did not end with status $SUCCESS but $result." +
                "\nVisit $jobUrl$buildNumber for further information"
        }
    }

    private fun extractResultJsonUrl(
        crumbWithId: CrumbWithId?,
        jobUrl: String,
        buildNumber: Int
    ): Promise<Pair<Int, String>> {
        val resultJsonRegex = Regex(
            "<div[^>]+id=\"buildHistoryPage\"[^>]*>[\\S\\s]*?" +
                "<td[^>]+class=\"build-row-cell[^>]+>[\\S\\s]*?" +
                "<a[^>]+href=\"[^\"]+pipeline.html#([^\"]+?)(?:&[^\"]+)?\"[^>]*>[\\S\\s]*?" +
                "</td>"
        )
        return pollAndExtract(crumbWithId, jobUrl, resultJsonRegex) { e ->
            throw IllegalStateException(
                "Could not find the published release json link." +
                    "\nJob URL: $jobUrl" +
                    "\nRegex used: ${resultJsonRegex.pattern}" +
                    extractFirstBuildRow(e)
            )
        }.then { buildNumber to it }
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


    private fun changeUrlAndReloadOrAddHint(jobUrl: String, buildNumber: Int, releaseJsonUrl: String) {
        val prefix = window.location.protocol + "//" + window.location.hostname + "/"
        val isOnSameHost = jobUrl.startsWith(prefix)
        if (isOnSameHost) {
            val pipelineUrl = window.location.href.substringBefore('#')
            val relativeJobUrl = jobUrl.substringAfter(prefix)
            val url = "$pipelineUrl#$releaseJsonUrl${App.PUBLISH_JOB}$relativeJobUrl"
            val successMsg = showSuccess(
                "Publishing successful, going to change to the new location." +
                    "\nIf this message does not disappear, then it means the switch failed. Please visit the following url manually:" +
                    "\n$url"
            )
            sleep(2000) {
                window.location.href = url
                successMsg.style.display = "none"
            }
        } else {
            showWarning(
                "The release.json was successfully published. " +
                    "However, since it is not on the same server, we cannot consume it." +
                    "\nVisit the publish job for further information: $jobUrl$buildNumber"
            )
        }
    }

    fun applyChanges(): Boolean {
        return modifiableJson.applyChanges()
    }

    companion object {
        private val resultRegex = Regex("<result>([A-Z]+)</result>")
        private const val SUCCESS = "SUCCESS"
    }
}
