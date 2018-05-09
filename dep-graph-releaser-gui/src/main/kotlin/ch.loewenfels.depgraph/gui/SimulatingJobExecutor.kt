package ch.loewenfels.depgraph.gui

import failAfterSteps
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
        jobExecutionData: JobExecutionData,
        jobQueuedHook: (queuedItemUrl: String) -> Promise<*>,
        jobStartedHook: (buildNumber: Int) -> Promise<*>,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int,
        verbose: Boolean
    ): Promise<Pair<CrumbWithId, Int>> {
        val jobName = jobExecutionData.jobName
        return sleep(100) {
            jobQueuedHook("${jobExecutionData.jobBaseUrl}queuingUrl")
            informIfStepWiseAndNotPublish("job $jobName queued", jobName)
        }.then {
            sleep(waitBetweenSteps) {
                jobStartedHook(100)
                informIfStepWiseAndNotPublish("job $jobName started", jobName)
            }
        }.then {
            sleep(waitBetweenSteps) {
                ++count
                if (count > failAfterSteps) check(false) { count = -3; "simulating a failure for $jobName" }
                informIfStepWise("job $jobName ended")
                    .then { true }
            }
        }.then {
            CrumbWithId("Jenkins-Crumb", "onlySimulation") to 100
        }
    }

    private fun informIfStepWiseAndNotPublish(msg: String, jobName: String): Promise<Unit> {
        return if (!jobName.startsWith("publish")) {
            informIfStepWise(msg)
        } else {
            Promise.resolve(Unit)
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
