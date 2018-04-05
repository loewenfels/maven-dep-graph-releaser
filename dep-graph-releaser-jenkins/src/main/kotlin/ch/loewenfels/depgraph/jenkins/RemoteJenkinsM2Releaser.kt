package ch.loewenfels.depgraph.jenkins

import okhttp3.*
import java.net.MalformedURLException
import java.net.URL
import java.util.*


/**
 * Used to create a release of an artifact with the m2release plugin on a remote jenkins host.
 * It basically triggers the job via the REST API and polls it to see if it completed
 */
class RemoteJenkinsM2Releaser internal constructor(
    private val httpClient: OkHttpClient,
    jenkinsBaseUrl: String,
    private val jenkinsUsername: String,
    private val jenkinsPassword: String,
    private val maxTriggerTries: Int,
    private val maxReleaseTimeInSeconds: Int,
    private val pollEverySecond: Int,
    private val parameters: Map<String, String>
) {

    constructor(
        jenkinsBaseUrl: String,
        jenkinsUsername: String,
        jenkinsPassword: String,
        maxTriggerTries: Int,
        maxReleaseTimeInSeconds: Int,
        pollEverySecond: Int,
        parameters: Map<String, String>
    ) : this(
        OkHttpClient(),
        jenkinsBaseUrl,
        jenkinsUsername,
        jenkinsPassword,
        maxTriggerTries,
        maxReleaseTimeInSeconds,
        pollEverySecond,
        parameters
    )

    private val jenkinsBaseUrl: String = if (jenkinsBaseUrl.endsWith("/")) {
        jenkinsBaseUrl.substring(0, jenkinsBaseUrl.length - 1)
    } else {
        jenkinsBaseUrl
    }

    init {
        require(jenkinsBaseUrl.startsWith("http")) {
            "jenkinsBaseUrl does not start with http: $jenkinsBaseUrl"
        }
        require(jenkinsUsername.isNotBlank()) {
            "jenkinsUsername was blank"
        }
        require(jenkinsPassword.isNotBlank()) {
            "jenkinsPassword was blank"
        }
        require(maxTriggerTries > 0) {
            "maxTriggerTries has to be greater than 0, given: $maxReleaseTimeInSeconds"
        }
        require(maxReleaseTimeInSeconds > 0) {
            "maxReleaseTimeInSeconds has to be greater than 0, given: $maxReleaseTimeInSeconds"
        }
        require(pollEverySecond > 0) {
            "pollEverySecond has to be greater than 0, given: $pollEverySecond"
        }
    }

    fun release(jobName: String, releaseVersion: String, nextDevVersion: String) {
        val buildNumber = triggerBuild(jobName, releaseVersion, nextDevVersion)
        pollForCompletion(jobName, buildNumber)
    }

    private fun triggerBuild(jobName: String, releaseVersion: String, nextDevVersion: String): Int {
        var count = 0
        lateinit var response: Response
        do {
            check(count < maxTriggerTries) {
                "Cannot trigger the build, response was not successful after $maxTriggerTries attempts." +
                    "\nJob: $jobName" +
                    "\nResponse: $response"
            }
            val postUrl = createUrl("${jobUrl(jobName)}/m2release/submit")
            val inputData = createInputData(releaseVersion, nextDevVersion)
            val body = RequestBody.create(FORM_URLENCODED, inputData)
            val request = Request.Builder()
                .url(postUrl)
                .addBasicAuthHeader()
                .post(body)
                .build()
            response = httpClient.newCall(request).execute()
            ++count
        } while (!response.isSuccessful)

        // We somehow have to get the build number.
        // Unfortunately it is not returned by jenkins that's why we need to extract it from the resulting HTML
        return extractBuildNumber(response, jobName)
    }

    private fun createUrl(urlSpec: String): URL {
        try {
            return URL(urlSpec)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException(
                "Cannot create a POST-URL. Most probably there is an error in the given jenkinsBaseUrl." +
                    "\nUrl: $urlSpec", e
            )
        }
    }

    private fun jobUrl(jobName: String) = "$jenkinsBaseUrl/job/$jobName"

    private fun Request.Builder.addBasicAuthHeader(): Request.Builder {
        val authEncBytes = Base64.getEncoder().encode("$jenkinsUsername:$jenkinsPassword".toByteArray())
        return header("Authorization", "Basic ${String(authEncBytes)}")
    }

    private fun createInputData(releaseVersion: String, nextDevVersion: String): String {
        return "releaseVersion=$releaseVersion" +
            "&developmentVersion=$nextDevVersion" +
            createJson()
    }

    private fun createJson(): String {
        val sb = StringBuilder("&json={\"isDryRun\":false")
        val itr = parameters.entries.iterator()
        if (itr.hasNext()) {
            sb.append(",\"parameter\":[")
            sb.appendParameter(itr.next())
            while (itr.hasNext()) {
                sb.append(",")
                sb.appendParameter(itr.next())
            }
            sb.append("]")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun StringBuilder.appendParameter(entry: Map.Entry<String, String>) {
        append("{")
            .append("\"name\":\"").append(entry.key).append("\",")
            .append("\"value\":\"").append(entry.value).append("\"")
        append("}")
    }

    private fun extractBuildNumber(response: Response, jobName: String): Int {
        val body = response.body()
        check(body != null) {
            "body of the response was null, cannot extract the build number." +
                "\nJob: $jobName" +
                "\nResponse: $response"
        }
        val content = body!!.string()
        val matchResult = builderNumberRegex.find(content)
        if (matchResult != null) {
            return matchResult.groupValues[1].toInt()
        }
        throw IllegalStateException(
            "Could not find the build number in the returned body." +
                "\nJob: $jobName" +
                "\nRegex used: ${builderNumberRegex.pattern}" +
                "\n50 first chars of the body: ${content.take(50)}"
        )
    }

    private fun pollForCompletion(jobName: String, buildNumber: Int) {
        val url = createUrl("${jobUrl(jobName)}/$buildNumber/api/xml?xpath=/*/result")
        val request = Request.Builder()
            .url(url)
            .build()
        var result: String?
        var count = 0
        val maxCount = (maxReleaseTimeInSeconds / pollEverySecond) +
            if (maxReleaseTimeInSeconds % pollEverySecond != 0) 1 else 0
        do {
            check(count < maxCount) {
                "Waited at least $maxReleaseTimeInSeconds seconds for the release to complete, aborting now." +
                    "\nJob: $jobName"
            }
            Thread.sleep(pollEverySecond * 1000L)
            val response = httpClient.newCall(request).execute()
            result = extractResult(response, response.body())
            ++count

        } while (result == null)

        check(result == "SUCCESS") {
            "Result of the run was not SUCCESS but $result" +
                "\nJob: $jobName"
        }
    }

    private fun extractResult(response: Response, body: ResponseBody?): String? {
        if (response.isSuccessful && body != null) {
            val matchResult = resultRegex.matchEntire(body.string())
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }
        return null
    }

    companion object {
        private val FORM_URLENCODED = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8")
        private val builderNumberRegex = Regex(
            "<div[^>]+id=\"buildHistoryPage\"[^>]*>[\\S\\s]*?" +
                "<td[^>]+class=\"build-row-cell[^>]+>[\\S\\s]*?" +
                "<a[^>]+href=\"[^\"]*/job/[^/]+/([0-9]+)/console\"[^>]*>[\\S\\s]*?" +
                "<img[^>]+src=\"[^\"]*/plugin/m2release[^>]+>[\\S\\s]*?" +
                "</td>"
        )
        private val resultRegex = Regex("<result>([A-Z]+)</result>")
    }
}
