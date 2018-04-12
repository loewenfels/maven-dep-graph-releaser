package ch.loewenfels.depgraph.gui

import PUBLISH_JOB
import checkStatus
import org.w3c.fetch.*
import kotlin.browser.window
import kotlin.js.Promise


fun publish(json: String, fileName: String, possiblyRelativePublishJobUrl: String) {
    require(!possiblyRelativePublishJobUrl.contains("://") || possiblyRelativePublishJobUrl.startsWith("http")) {
        "The publish job URL does not start with http but contains ://"
    }

    val jobUrl = getJobUrl(possiblyRelativePublishJobUrl)
    val jenkinsUrl = jobUrl.substringBefore("/job/")
    changeCursorToProgress()
    issueCrumb(jenkinsUrl)
        .then { crumbWithId: Pair<String, String>? ->
            post(jobUrl, crumbWithId, fileName, json)
        }.then(::checkStatus)
        .catch {
            throw Error("Could not trigger the publish job", it)
        }.then { _: String ->
            showInfo("Triggered publish job successfully, wait for completion...", 2000)
            //POST does not return anything, that's why we don't pass anything
            extractBuildNumber(fileName, jobUrl)
        }.then { buildNumber: Int ->
            pollJobForCompletion(jobUrl, buildNumber)
                .then { result -> buildNumber to result }
        }.then { (buildNumber, result) ->
            checkJobResult(jobUrl, buildNumber, result)
        }.then { buildNumber ->
            extractResultJsonUrl(jobUrl, buildNumber)
        }.then { (buildNumber, releaseJsonUrl) ->
            changeUrlAndReloadOrAddHint(jobUrl, buildNumber, releaseJsonUrl)
        }.catch {
            showError(it)
        }.finally {
            changeCursorBackToNormal()
        }
}

private fun getJobUrl(possiblyRelativePublishJobUrl: String): String {
    val prefix = window.location.protocol + "//" + window.location.hostname + "/"
    val tmpUrl = if (possiblyRelativePublishJobUrl.contains("://")) {
        possiblyRelativePublishJobUrl
    } else {
        prefix + possiblyRelativePublishJobUrl
    }
    return if (tmpUrl.endsWith("/")) tmpUrl else "$tmpUrl/"
}

fun issueCrumb(jenkinsUrl: String): Promise<Pair<String, String>?> {
    val init = js("({})")
    init.credentials = "include"
    val url = "$jenkinsUrl/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)"
    @Suppress("UnsafeCastFromDynamic")
    return window.fetch(url, init)
        .then(::checkStatusOkOr404)
        .catch {
            throw Error("Cannot issue a crumb", it)
        }.then { crumbWithId: String? ->
            if (crumbWithId != null) {
                val (id, crumb) = crumbWithId.split(':')
                id to crumb
            } else {
                null
            }
        }
}

fun checkStatusOkOr404(response: Response): Promise<String?> {
    return response.text().then { text ->
        if (response.status == 404.toShort()) {
            null
        } else {
            check(response.ok) { "response was not ok, ${response.status}: ${response.statusText}\n$text" }
            text
        }
    }
}

private fun post(
    jobUrl: String,
    crumbPair: Pair<String, String>?,
    fileName: String,
    newJson: String
): Promise<Response> {

    val headers = js("({})")
    headers["content-type"] = "application/x-www-form-urlencoded; charset=utf-8"
    val mode = if (crumbPair != null) {
        headers[crumbPair.first] = crumbPair.second
        RequestMode.CORS
    } else {
        RequestMode.NO_CORS
    }
    val init = RequestInit(
        body = "fileName=$fileName&json=$newJson",
        method = "POST",
        headers = headers,
        mode = mode,
        cache = org.w3c.fetch.RequestCache.NO_CACHE,
        redirect = org.w3c.fetch.RequestRedirect.FOLLOW,
        credentials = RequestCredentials.INCLUDE
    )
    //have to remove property because RequestInit sets it to null which is not valid
    js(
        "delete init.integrity;" +
            "delete init.referer;" +
            "delete init.referrerPolicy;" +
            "delete init.keepalive;" +
            "delete init.window;"
    )
    return window.fetch("${jobUrl}buildWithParameters", init)
}

private fun pollJobForCompletion(jobUrl: String, buildNumber: Int): Promise<String> {
    return poll("$jobUrl$buildNumber/api/xml?xpath=/*/result", 0, { body ->
        val matchResult = resultRegex.matchEntire(body)
        if (matchResult != null) {
            true to matchResult.groupValues[1]
        } else {
            false to ""
        }
    })
}

private fun extractBuildNumber(fileName: String, jobUrl: String): Promise<Int> {
    val buildNumberRegex = Regex(
        "<div[^>]+id=\"buildHistoryPage\"[^>]*>[\\S\\s]*?" +
            "<td[^>]+class=\"build-row-cell[^>]+>[\\S\\s]*?" +
            "<a[^>]+href=\"[^\"]*/job/[^/]+/([0-9]+)/console\"[^>]*>[\\S\\s]*?" +
            "<a[^>]+class=\"[^\"]+build-link[^>]+>#[0-9]+ $fileName[^<]*</a>[\\S\\s]*?" +
            "</td>"
    )
    return pollAndExtract(jobUrl, buildNumberRegex) { e ->
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

private fun pollAndExtract(url: String, regex: Regex, errorHandler: (PollException) -> Nothing): Promise<String> {
    return poll(url, 0, { body ->
        val matchResult = regex.find(body)
        if (matchResult != null) {
            true to matchResult.groupValues[1]
        } else {
            false to null
        }
    }).catch { t -> errorHandler(t as PollException) }
}

fun checkJobResult(jobUrl: String, buildNumber: Int, result: String): Int {
    check(result == SUCCESS) {
        "Publishing the json failed, job did not end with status $SUCCESS but $result." +
            "\nVisit $jobUrl$buildNumber for further information"
    }
    return buildNumber
}

fun extractResultJsonUrl(jobUrl: String, buildNumber: Int): Promise<Pair<Int, String>> {
    val resultJsonRegex = Regex(
        "<div[^>]+id=\"buildHistoryPage\"[^>]*>[\\S\\s]*?" +
            "<td[^>]+class=\"build-row-cell[^>]+>[\\S\\s]*?" +
            "<a[^>]+href=\"[^\"]+pipeline.html#([^\"]+)\"[^>]*>[\\S\\s]*?" +
            "</td>"
    )
    return pollAndExtract(jobUrl, resultJsonRegex) { e ->
        throw IllegalStateException(
            "Could not find the published release json link." +
                "\nJob URL: $jobUrl" +
                "\nRegex used: ${resultJsonRegex.pattern}" +
                extractFirstBuildRow(e)
        )
    }.then { buildNumber to it }
}

private fun <T : Any> poll(
    pollUrl: String,
    numberOfTries: Int,
    action: (String) -> Pair<Boolean, T?>,
    maxNumberOfTries: Int = 10,
    sleepInSeconds: Int = 2
): Promise<T> {
    return window.fetch(pollUrl)
        .then(::checkStatus)
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
                    poll(pollUrl, numberOfTries + 1, action)
                }
                // asDynamic is used because javascript resolves the result automatically on return
                // wont result in Promise<T> but T
                p.asDynamic() as T

            }
        }
}

class PollException(message: String, val body: String) : RuntimeException(message)


fun changeUrlAndReloadOrAddHint(
    jobUrl: String,
    buildNumber: Int,
    releaseJsonUrl: String
) {
    val prefix = window.location.protocol + "//" + window.location.hostname + "/"
    val isOnSameHost = jobUrl.startsWith(prefix)
    if (isOnSameHost) {
        val pipelineUrl = window.location.href.substringBefore('#')
        val url = "$pipelineUrl#$releaseJsonUrl$PUBLISH_JOB${jobUrl.substringAfter(prefix)}"
        showSuccess("Publishing successful, going to change the location." +
            "\nIf this message does not disappear, then it means the reload failed. Please visit the following url manually:" +
            "\n$url")
        //TODO jobUrl is appended twice under some circumstances, once with &amp;
        window.location.href = url
        window.location.reload()
    } else {
        showWarning(
            "The release.json was successfully published. " +
                "However, since it is not on the same server, we cannot consume it." +
                "\nVisit the publish job for further information: $jobUrl$buildNumber"
        )
    }
}

private val resultRegex = Regex("<result>([A-Z]+)</result>")
private const val SUCCESS = "SUCCESS"
