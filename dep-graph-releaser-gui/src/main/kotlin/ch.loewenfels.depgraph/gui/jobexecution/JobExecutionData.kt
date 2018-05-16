package ch.loewenfels.depgraph.gui.jobexecution

@Suppress("DataClassPrivateConstructor")
data class JobExecutionData private constructor(
    val jobName: String,
    val jobBaseUrl: String,
    val jobTriggerUrl: String,
    val body: String
) {
    companion object {
        /**
         * Uses "[jobBaseUrl]buildWithParameters" for [jobTriggerUrl].
         */
        fun buildWithParameters(jobName: String, jobBaseUrl: String, body: String): JobExecutionData {
            val jobBaseUrlWithSlash =
                assureEndsWithSlash(jobBaseUrl)
            return create(
                jobName,
                jobBaseUrlWithSlash,
                "${jobBaseUrlWithSlash}buildWithParameters",
                body
            )
        }

        fun create(jobName: String, jobBaseUrl: String, jobTriggerUrl: String, body: String): JobExecutionData {
            val jobBaseUrlWithSlash =
                assureEndsWithSlash(jobBaseUrl)
            return JobExecutionData(
                jobName,
                jobBaseUrlWithSlash,
                jobTriggerUrl,
                body
            )
        }

        private fun assureEndsWithSlash(jobBaseUrl: String) =
            if (jobBaseUrl.endsWith("/")) jobBaseUrl else "$jobBaseUrl/"
    }
}
