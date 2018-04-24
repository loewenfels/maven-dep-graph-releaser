package ch.loewenfels.depgraph.gui

import kotlin.js.Promise

interface JobExecutor {

    fun trigger(
        jobUrl: String,
        jobName: String,
        body: String,
        jobQueuedHook: (queuedItemUrl: String) -> Promise<*>,
        jobStartedHook: (buildNumber: Int) -> Promise<*>,
        verbose: Boolean = true
    ): Promise<Pair<CrumbWithId, Int>>

    fun pollAndExtract(
        crumbWithId: CrumbWithId?,
        url: String,
        regex: Regex,
        errorHandler: (JenkinsJobExecutor.PollException) -> Nothing
    ): Promise<String>
}
