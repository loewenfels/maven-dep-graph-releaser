package ch.loewenfels.depgraph.gui.jobexecution

import kotlin.js.Promise

fun extractBuildNumber(
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
