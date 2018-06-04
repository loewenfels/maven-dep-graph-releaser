package ch.loewenfels.depgraph.gui.jobexecution

import kotlin.js.Promise

interface JobExecutor {

    fun trigger(
        jobExecutionData: JobExecutionData,
        jobQueuedHook: (queuedItemUrl: String) -> Promise<*>,
        jobStartedHook: (buildNumber: Int) -> Promise<*>,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int,
        verbose: Boolean = true
    ): Promise<Pair<CrumbWithId, Int>>

    fun pollAndExtract(
        usernameAndApiToken: UsernameAndApiToken,
        crumbWithId: CrumbWithId?,
        url: String,
        regex: Regex,
        errorHandler: (PollException) -> Nothing
    ): Promise<String>
}
