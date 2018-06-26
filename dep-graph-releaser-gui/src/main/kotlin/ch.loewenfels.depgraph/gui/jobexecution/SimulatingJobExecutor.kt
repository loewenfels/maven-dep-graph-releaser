package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.gui.showAlert
import ch.loewenfels.depgraph.gui.sleep
import failAfterSteps
import stepWise
import waitBetweenSteps
import kotlin.js.Promise

class SimulatingJobExecutor : JobExecutor {
    private var count = 0

    override fun pollAndExtract(
        authData: AuthData,
        url: String,
        regex: Regex,
        pollEverySecond: Int,
        maxWaitingTimeInSeconds: Int,
        errorHandler: (PollTimeoutException) -> Nothing
    ): Promise<String> {
        return Promise.resolve("simulation-only.json")
    }

    override fun trigger(
        jobExecutionData: JobExecutionData,
        jobQueuedHook: (queuedItemUrl: String?) -> Promise<*>,
        jobStartedHook: (buildNumber: Int) -> Promise<*>,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int,
        verbose: Boolean
    ): Promise<Pair<AuthData, Int>> {
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
                simulateJobFinished(jobExecutionData)
            }
        }.then {
            getFakeAuthDataAndBuildNumber()
        }
    }

    private fun informIfStepWiseAndNotPublish(msg: String, jobName: String): Promise<Unit> {
        return if (!jobName.startsWith("publish")) {
            informIfStepWise(msg)
        } else {
            Promise.resolve(Unit)
        }
    }

    override fun rePoll(
        jobExecutionData: JobExecutionData,
        buildNumber: Int,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int
    ): Promise<Pair<AuthData, Int>> {
        return sleep(waitBetweenSteps) {
            simulateJobFinished(jobExecutionData)
        }.then {
            getFakeAuthDataAndBuildNumber()
        }
    }

    private fun simulateJobFinished(jobExecutionData: JobExecutionData): Promise<Boolean> {
        ++count
        if (count > failAfterSteps) check(false) {
            count = -3; "simulating a failure for ${jobExecutionData.jobName}"
        }
        return informIfStepWise("job ${jobExecutionData.jobName} ended")
            .then { true }
    }

    private fun informIfStepWise(msg: String): Promise<Unit> {
        return if (stepWise) {
            showAlert(msg)
        } else {
            Promise.resolve(Unit)
        }
    }

    private fun getFakeAuthDataAndBuildNumber(): Pair<AuthData, Int> {
        return AuthData(
            UsernameAndApiToken("simulating-user", "random-api-token"),
            CrumbWithId("Jenkins-Crumb", "onlySimulation")
        ) to 100
    }
}
