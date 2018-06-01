package ch.loewenfels.depgraph.jenkins

import okhttp3.*
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
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
    private val pollExecutionEverySecond: Int,
    private val maxWaitForExecutionInSeconds: Int,
    private val pollReleaseEverySecond: Int,
    private val maxReleaseTimeInSeconds: Int,
    private val parameters: Map<String, String>
) {

    constructor(
        jenkinsBaseUrl: String,
        jenkinsUsername: String,
        jenkinsPassword: String,
        maxTriggerTries: Int,
        pollExecutionEverySecond: Int,
        maxWaitForExecutionInSeconds: Int,
        pollReleaseEverySecond: Int,
        maxReleaseTimeInSeconds: Int,
        parameters: Map<String, String>
    ) : this(
        { OkHttpClient() },
        jenkinsBaseUrl,
        jenkinsUsername,
        jenkinsPassword,
        maxTriggerTries,
        pollExecutionEverySecond,
        maxWaitForExecutionInSeconds,
        pollReleaseEverySecond,
        maxReleaseTimeInSeconds,
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
        require(pollExecutionEverySecond > 0) {
            "pollExecutionEverySecond has to be greater than 0, given: $pollExecutionEverySecond"
        }
        require(maxWaitForExecutionInSeconds > 0) {
            "maxWaitForExecutionInSeconds has to be greater than 0, given: $maxWaitForExecutionInSeconds"
        }
        require(pollReleaseEverySecond > 0) {
            "pollReleaseEverySecond has to be greater than 0, given: $pollReleaseEverySecond"
        }
        require(maxReleaseTimeInSeconds > 0) {
            "maxReleaseTimeInSeconds has to be greater than 0, given: $maxReleaseTimeInSeconds"
        }
    }

    fun release(jobName: String, releaseVersion: String, nextDevVersion: String) {
        val httpClient = httpClientFactory()
        try {
            val buildNumber = triggerBuild(httpClient, jobName, releaseVersion, nextDevVersion)
            logTriggeringSuccessful(jobName, buildNumber)
            val result = pollForCompletion(httpClient, jobName, buildNumber)
            checkResultIsSuccess(result, jobName, buildNumber)
        } finally {
            shutdown(httpClient)
        }
    }

    private fun logTriggeringSuccessful(jobName: String, buildNumber: Int) {
        logger.info(
            "triggering was successful, will wait for the job to complete." +
                "\nVisit ${jobUrl(jobName)}/$buildNumber for detailed information"
        )
    }

    private fun checkResultIsSuccess(result: String, jobName: String, buildNumber: Int) {
        check(result == "SUCCESS") {
            "Job status was not SUCCESS but $result" +
                "\nJob: $jobName" +
                "\nVisit ${jobUrl(jobName)}/$buildNumber for more details"

        }
    }

    private fun triggerBuild(
        httpClient: OkHttpClient,
        jobName: String,
        releaseVersion: String,
        nextDevVersion: String
    ): Int {
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

        return extractBuildNumber(httpClient, jobName, releaseVersion, nextDevVersion)
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
                "Cannot create a URL. Most probably there is an error in the given jenkinsBaseUrl." +
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

    private fun extractBuildNumber(
        httpClient: OkHttpClient,
        jobName: String,
        releaseVersion: String,
        nextDevVersion: String
    ): Int {
        // We somehow have to get the build number. Unfortunately it is not returned by M2Plugin from the POST request
        // (also not in header) that's why we first check if there is a queued item matching our versions
        // if so we can extract the build number as soon as the job is no longer queued but executed
        // otherwise (execution is already running; no queued item), we need to extract the build number
        // from the resulting HTML

        val content = fetch(jobName, "/api/xml", httpClient, "job-overview-api-xml response")
        val queueItemRegex = Regex(
            "<queueItem>[\\S\\s]*?" +
                "<id>([0-9]+)</id>[\\S\\s]*?" +
                "<params>[\\S\\s]*?" +
                "MVN_RELEASE_VERSION=$releaseVersion MVN_DEV_VERSION=$nextDevVersion[\\S\\s]*?" +
                "</queueItem>"
        )
        val matchResult = queueItemRegex.find(content)
        return if (matchResult != null) {
            val queuedItemId = matchResult.groupValues[1].toInt()
            logger.info(
                "Going to extract the build number from the queued item $queuedItemId" +
                    "\nVisit ${getQueueItemUrl(queuedItemId)} for more details."
            )
            extractBuildNumberFromQueuedItem(httpClient, queuedItemId, jobName)
        } else {
            logger.info("Going to extract the build number from HTML, the most recent M2 Release build is considered")
            extractBuildNumberFromHtml(httpClient, jobName)
        }
    }

    private fun getQueueItemUrl(queuedItemId: Int): String {
        return "$jenkinsBaseUrl/queue/item/$queuedItemId/api/xml?xpath=//executable/number"
    }

    private fun fetch(jobName: String, suffix: String, httpClient: OkHttpClient, responseName: String): String {
        val jobOverviewXmlUrl = createUrl("${jobUrl(jobName)}$suffix")
        val request = Request.Builder().url(jobOverviewXmlUrl).build()
        val response = httpClient.newCall(request).execute()
        check(response.isSuccessful) {
            "Cannot fetch, $responseName was not OK." +
                "\nJob: $jobName" +
                "\nResponse: $response"
        }
        return checkBodyNotNull(response, responseName, jobName)
    }

    private fun checkBodyNotNull(response: Response, responseName: String, jobName: String): String {
        val body = response.body()
        check(body != null) {
            "Body of the $responseName was null, cannot extract the build number." +
                "\nJob: $jobName" +
                "\nResponse: $response"
        }
        val content = body!!.string()
        body.close()
        return content
    }

    private fun extractBuildNumberFromQueuedItem(httpClient: OkHttpClient, queuedItemId: Int, jobName: String): Int {
        val queueItemUrl = createUrl(getQueueItemUrl(queuedItemId))
        val buildNumber = pollAndExtract(
            httpClient,
            queueItemUrl,
            jobName,
            "queued",
            numberRegex,
            pollExecutionEverySecond,
            maxWaitForExecutionInSeconds
        )
        return buildNumber.toInt()
    }


    private fun extractBuildNumberFromHtml(httpClient: OkHttpClient, jobName: String): Int {
        val content = fetch(jobName, "", httpClient, "job-overview-html response")
        val matchResult = builderNumberRegex.find(content)
        if (matchResult != null) {
            //TODO we could additionally verify that it is the correct build by calling:
            // https://jenkinsBaseUrl/job/JOB_NAME/BUILD_NUMBER/api/xml and check that parameter MVN_RELEASE_VERSION and MVN_DEV_VERSION are correct
            return matchResult.groupValues[1].toInt()
        }
        throw IllegalStateException(
            "Could not find the build number in the returned body." +
                "\nJob: $jobName" +
                "\nRegex used: ${builderNumberRegex.pattern}" +
                "\nFollowing the content of the first build-row:\n" + extractFirstBuildRow(content)
        )
    }

    private fun extractFirstBuildRow(content: String): String {
        val regex = Regex("<tr[^>]+class=\"[^\"]*build-row[^\"][^>]*>([\\S\\s]*?)</tr>")
        return regex.find(content)?.value ?: "<nothing found, 100 first chars of body instead:>\n${content.take(100)}"
    }

    private fun pollForCompletion(httpClient: OkHttpClient, jobName: String, buildNumber: Int): String {
        val pollUrl = createUrl("${jobUrl(jobName)}/$buildNumber/api/xml?xpath=/*/result")
        return pollAndExtract(
            httpClient,
            pollUrl,
            jobName,
            "completed",
            resultRegex,
            pollReleaseEverySecond,
            maxReleaseTimeInSeconds
        )
    }

    private fun pollAndExtract(
        httpClient: OkHttpClient,
        pollUrl: URL,
        jobName: String,
        waitingFor: String,
        regex: Regex,
        pollEverySecond: Int,
        maxTimeInSeconds: Int
    ): String {
        val request = Request.Builder().url(pollUrl).build()
        var result: String?
        val maxCount = calculateMaxCount(pollEverySecond, maxTimeInSeconds)
        val minuteInterval = calculateMinuteInterval(pollEverySecond)
        var count = 0
        do {
            checkTimeoutNotYetReached(count, maxCount, jobName, waitingFor)
            Thread.sleep(pollEverySecond * 1000L)
            val response = httpClient.newCall(request).execute()
            result = extractResult(response, regex)
            ++count
            if (count % minuteInterval == 0) {
                logger.info("$jobName not yet $waitingFor after at least ${count * pollEverySecond} seconds, we are still polling")
            }
        } while (result == null)
        return result
    }


    private fun calculateMaxCount(pollEverySecond: Int, maxTimeInSeconds: Int): Int {
        val max = maxTimeInSeconds / pollEverySecond
        return if (maxTimeInSeconds % pollEverySecond == 0) max else max + 1
    }

    private fun calculateMinuteInterval(pollEverySecond: Int): Int {
        val tmp = 60 / pollEverySecond
        return if (tmp != 0) tmp else 1
    }

    private fun checkTimeoutNotYetReached(count: Int, maxCount: Int, jobName: String, waitingFor: String) {
        check(count < maxCount) {
            "Waited at least $maxReleaseTimeInSeconds seconds for the release, still not $waitingFor, aborting now." +
                "\nJob: $jobName"
        }
    }

    private fun extractResult(response: Response, regex: Regex): String? {
        val body = response.body()
        if (response.isSuccessful && body != null) {
            val matchResult = regex.matchEntire(body.string())
            body.close()
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }
        return null
    }

    private fun shutdown(httpClient: OkHttpClient) {
        httpClient.dispatcher()?.cancelAll()
        httpClient.connectionPool()?.evictAll()
        httpClient.cache()?.close()
        httpClient.dispatcher()?.executorService()?.extendedShutdown()
    }

    private fun ExecutorService.extendedShutdown() {
        try {
            shutdown()
            if (!awaitTermination(1, TimeUnit.SECONDS)) {
                shutdownNow()
            }
        } catch (ex: InterruptedException) {
            shutdownNow()
            Thread.currentThread().interrupt()
        }
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
        private val numberRegex = Regex("<number>([0-9]+)</number>")
        private val resultRegex = Regex("<result>([A-Z]+)</result>")
        private val logger = Logger.getLogger(RemoteJenkinsM2Releaser::class.java.simpleName)
    }
}
