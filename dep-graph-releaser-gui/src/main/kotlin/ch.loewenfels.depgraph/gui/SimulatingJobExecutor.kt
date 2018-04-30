package ch.loewenfels.depgraph.gui

import kotlin.js.Promise

class SimulatingJobExecutor : JobExecutor {
    private var count = 0
    override fun pollAndExtract(
        crumbWithId: CrumbWithId?,
        url: String,
        regex: Regex,
        errorHandler: (JenkinsJobExecutor.PollException) -> Nothing
    ): Promise<String> {
        return Promise.resolve("<filename>simulation-only.json</filename>")
    }

    override fun trigger(
        jobUrl: String,
        jobName: String,
        body: String,
        jobQueuedHook: (queuedItemUrl: String) -> Promise<*>,
        jobStartedHook: (buildNumber: Int) -> Promise<*>,
        pollEverySecond: Int,
        maxWaitingTimeForCompleteness: Int,
        verbose: Boolean
    ): Promise<Pair<CrumbWithId, Int>> {
        return sleep(100) {
            jobQueuedHook("${jobUrl}queuingUrl")
        }.then {
            sleep(300) {
                jobStartedHook(100)
            }
        }.then {
            sleep(300) {
                ++count
//                if(count > 8) check(false) { "simulating a failure for $jobName" }
                true
            }
        }.then {
            CrumbWithId("Jenkins-Crumb", "onlySimulation") to 100
        }
    }

}
