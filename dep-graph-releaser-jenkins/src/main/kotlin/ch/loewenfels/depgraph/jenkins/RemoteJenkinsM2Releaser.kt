package ch.loewenfels.depgraph.jenkins

import okhttp3.*
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.logging.Logger


/**
 * Used to create a release of an artifact with the m2release plugin on a remote jenkins host.
 * It basically triggers the job via the REST API and polls it to see if it completed
 */
class RemoteJenkinsM2Releaser internal constructor(
    private val httpClientFactory: () -> OkHttpClient,
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
        { OkHttpClient() },
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
        val httpClient = httpClientFactory()
        try {
            val buildNumber = triggerBuild(httpClient, jobName, releaseVersion, nextDevVersion)

            logTriggeringSuccessful(jobName, buildNumber)
            val result = pollForCompletion(httpClient, jobName, buildNumber)

            check(result == "SUCCESS") {
                "Job status was not SUCCESS but $result" +
                    "\nJob: $jobName"
            }
        } finally {
            httpClient.dispatcher()?.executorService()?.shutdown()
            httpClient.connectionPool()?.evictAll()
            httpClient.cache()?.close()
        }
    }

    private fun logTriggeringSuccessful(jobName: String, buildNumber: Int) {
        logger.info(
            "triggering was successful, will wait for the job to complete." +
                "\nVisit ${jobUrl(jobName)}/$buildNumber for detailed information"
        )
    }

    private fun triggerBuild(httpClient: OkHttpClient, jobName: String, releaseVersion: String, nextDevVersion: String): Int {
        val postUrl = createUrl("${jobUrl(jobName)}/m2release/submit")
        lateinit var response: Response
        var count = 0
        do {
            // we wrap response so that it is not accessed the first time when it is not yet initialised
            checkMaximumTriesNotYetReached(count, jobName, { response })
            response = post(httpClient, postUrl, releaseVersion, nextDevVersion)
            ++count
            if (count % 3 == 0) {
                logger.info("still no luck after triggering $jobName the $count time")
            }
        } while (!response.isSuccessful)

        // We somehow have to get the build number.
        // Unfortunately it is not returned by jenkins that's why we need to extract it from the resulting HTML
        return extractBuildNumber(response, jobName)
    }

    private inline fun checkMaximumTriesNotYetReached(count: Int, jobName: String, getResponse: () -> Response) {
        check(count < maxTriggerTries) {
            val response = getResponse()
            "Cannot trigger the build, response was not successful after $maxTriggerTries attempts." +
                "\nJob: $jobName" +
                "\nResponse: $response"
        }
    }

    private fun post(httpClient: OkHttpClient, postUrl: URL, releaseVersion: String, nextDevVersion: String): Response {
        val inputData = createInputData(releaseVersion, nextDevVersion)
        val body = RequestBody.create(FORM_URLENCODED, inputData)
        val request = Request.Builder()
            .url(postUrl)
            .addBasicAuthHeader()
            .post(body)
            .build()
        return httpClient.newCall(request).execute()
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
        body.close()
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

    private fun pollForCompletion(httpClient: OkHttpClient, jobName: String, buildNumber: Int): String {
        val pollUrl = createUrl("${jobUrl(jobName)}/$buildNumber/api/xml?xpath=/*/result")
        val request = Request.Builder().url(pollUrl).build()
        var result: String?
        val maxCount = calculateMaxCount()
        val minuteInterval = calculateMinuteInterval()
        var count = 0
        do {
            checkTimeoutNotYetReached(count, maxCount, jobName)
            Thread.sleep(pollEverySecond * 1000L)
            val response = httpClient.newCall(request).execute()
            result = extractResult(response, response.body())
            ++count
            if (count % minuteInterval == 0) {
                logger.info("$jobName did not complete after at least ${count * pollEverySecond} seconds")
            }
        } while (result == null)
        return result
    }

    private fun calculateMaxCount(): Int {
        val max = maxReleaseTimeInSeconds / pollEverySecond
        return if (maxReleaseTimeInSeconds % pollEverySecond == 0) max else max + 1
    }

    private fun calculateMinuteInterval(): Int {
        val tmp = 60 / pollEverySecond
        return if (tmp != 0) tmp else 1
    }

    private fun checkTimeoutNotYetReached(count: Int, maxCount: Int, jobName: String) {
        check(count < maxCount) {
            "Waited at least $maxReleaseTimeInSeconds seconds for the release to complete, aborting now." +
                "\nJob: $jobName"
        }
    }

    private fun extractResult(response: Response, body: ResponseBody?): String? {
        if (response.isSuccessful && body != null) {
            val matchResult = resultRegex.matchEntire(body.string())
            body.close()
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
        private val logger = Logger.getLogger(RemoteJenkinsM2Releaser::class.java.simpleName)
    }
}
