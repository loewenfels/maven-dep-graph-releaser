package ch.loewenfels.depgraph.gui.jobexecution

@Suppress("DataClassPrivateConstructor")
data class JobExecutionData private constructor(
    val jobName: String,
    val jobBaseUrl: String,
    val jobTriggerUrl: String,
    val body: String,
    val identifyingParams: Map<String, String>,
    val queuedItemUrlExtractor: QueuedItemUrlExtractor
) {
    fun getJenkinsBaseUrl(): String = jobBaseUrl.substringBefore("/job/")

    companion object {
        /**
         * Uses "[jobBaseUrl]buildWithParameters" for [jobTriggerUrl].
         */
        fun buildWithParameters(
            jobName: String,
            jobBaseUrl: String,
            body: String,
            identifyingParams: Map<String, String>
        ): JobExecutionData {
            val jobBaseUrlWithSlash = assureEndsWithSlash(jobBaseUrl)
            val jobTriggerUrl = "${jobBaseUrlWithSlash}buildWithParameters"
            return create(
                jobName,
                jobBaseUrlWithSlash,
                jobTriggerUrl,
                body,
                identifyingParams,
                LocationBasedQueuedItemUrlExtractor
            )
        }

        /**
         * Uses "[jobBaseUrl]m2release/submit" for [jobTriggerUrl].
         */
        fun m2ReleaseSubmit(
            jobName: String,
            jobBaseUrl: String,
            body: String,
            releaseVersion: String,
            nextDevVersion: String
        ): JobExecutionData {
            val jobBaseUrlWithSlash = assureEndsWithSlash(jobBaseUrl)
            val jobTriggerUrl = "${jobBaseUrlWithSlash}m2release/submit"
            val identifyingParams = mapOf(
                "MVN_RELEASE_VERSION" to releaseVersion,
                "MVN_DEV_VERSION" to nextDevVersion
            )
            val queuedItemUrlExtractor = RestApiBasedQueuedItemUrlExtractor(identifyingParams)
            return create(jobName, jobBaseUrlWithSlash, jobTriggerUrl, body, identifyingParams, queuedItemUrlExtractor)
        }

        private fun create(
            jobName: String,
            jobBaseUrl: String,
            jobTriggerUrl: String,
            body: String,
            identifyingParams: Map<String, String>,
            queuedItemUrlExtractor: QueuedItemUrlExtractor
        ): JobExecutionData {
            val jobBaseUrlWithSlash = assureEndsWithSlash(jobBaseUrl)
            return JobExecutionData(
                jobName,
                jobBaseUrlWithSlash,
                jobTriggerUrl,
                body,
                identifyingParams,
                queuedItemUrlExtractor
            )
        }

        private fun assureEndsWithSlash(jobBaseUrl: String) =
            if (jobBaseUrl.endsWith("/")) jobBaseUrl else "$jobBaseUrl/"
    }
}
