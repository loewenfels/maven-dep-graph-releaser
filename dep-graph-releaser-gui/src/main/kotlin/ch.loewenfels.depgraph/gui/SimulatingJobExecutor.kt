package ch.loewenfels.depgraph.gui

import failAfter
import stepWise
import waitBetweenSteps
import kotlin.js.Promise

class SimulatingJobExecutor : JobExecutor {
    private var count = 0
    override fun pollAndExtract(
        crumbWithId: CrumbWithId?,
        url: String,
        regex: Regex,
        errorHandler: (JenkinsJobExecutor.PollException) -> Nothing
    ): Promise<String> {
        return Promise.resolve("simulation-only.json")
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
            informIfStepWise("job $jobName queued")
        }.then {
            sleep(waitBetweenSteps) {
                jobStartedHook(100)
                informIfStepWise("job $jobName started")
            }
        }.then {
            sleep(waitBetweenSteps) {
                ++count
                if (count > failAfter) check(false) { count = -3; "simulating a failure for $jobName" }
                informIfStepWise("job $jobName ended")
                    .then { true }
            }
        }.then {
            CrumbWithId("Jenkins-Crumb", "onlySimulation") to 100
        }
    }

    private fun informIfStepWise(msg: String): Promise<Unit> {
        return if (stepWise) {
            showAlert(msg)
        } else {
            Promise.resolve(Unit)
        }
    }

}
