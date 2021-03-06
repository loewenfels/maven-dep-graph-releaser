package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.gui.components.showAlert
import ch.loewenfels.depgraph.gui.jobexecution.exceptions.PollTimeoutException
import ch.loewenfels.depgraph.gui.sleep
import ch.loewenfels.depgraph.gui.unwrapPromise
import failAfterSteps
import failDuringQueueing
import failWithTimeout
import stepWise
import waitBetweenSteps
import kotlin.js.Promise

class SimulatingJobExecutor : JobExecutor {

    private var count = 0

    override fun pollAndExtract(
        crumbWithId: CrumbWithId?,
        url: String,
        regex: Regex,
        pollEverySecond: Int,
        maxWaitingTimeInSeconds: Int,
        errorHandler: (PollTimeoutException) -> Nothing
    ): Promise<String> = Promise.resolve("simulation-only.json")

    override fun trigger(
        jobExecutionData: JobExecutionData,
        jobQueuedHook: (queuedItemUrl: String?) -> Promise<*>,
        jobStartedHook: (buildNumber: Int) -> Promise<*>,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int,
        verbose: Boolean
    ): Promise<Pair<CrumbWithId?, Int>> {
        val jobName = jobExecutionData.jobName
        return sleep(100) {
            jobQueuedHook("${jobExecutionData.jobBaseUrl}queuingUrl")
            informIfStepWiseAndNotPublish("job $jobName queued", jobName)
            if (isNotPublishJob(jobExecutionData)) {
                ++count
            }
            if (failDuringQueueing) {
                checkIfNeedsToFail(jobExecutionData)
            }
        }.then {
            sleep(waitBetweenSteps) {
                simulateBuildNumberExtracted(jobName, jobStartedHook)
            }
        }.then {
            sleep(waitBetweenSteps) {
                simulateJobFinished(jobExecutionData)
            }
        }.then {
            getFakeCrumbWithIdAndBuildNumber()
        }
    }

    private fun simulateBuildNumberExtracted(
        jobName: String,
        jobStartedHook: (buildNumber: Int) -> Promise<*>
    ): Promise<Unit> {
        jobStartedHook(100)
        return informIfStepWiseAndNotPublish("job $jobName started", jobName)
    }

    private fun informIfStepWiseAndNotPublish(msg: String, jobName: String): Promise<Unit> =
        if (!jobName.startsWith("publish")) informIfStepWise(msg)
        else Promise.resolve(Unit)

    override fun rePollQueueing(
        jobExecutionData: JobExecutionData,
        queuedItemUrl: String,
        jobStartedHook: (buildNumber: Int) -> Promise<*>,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int
    ): Promise<Pair<CrumbWithId?, Int>> {
        return sleep(waitBetweenSteps) {
            simulateBuildNumberExtracted(jobExecutionData.jobName, jobStartedHook)
        }.then {
            rePoll(
                jobExecutionData, 100, pollEverySecond, maxWaitingTimeForCompletenessInSeconds
            )
        }.unwrapPromise()
    }

    override fun rePoll(
        jobExecutionData: JobExecutionData,
        buildNumber: Int,
        pollEverySecond: Int,
        maxWaitingTimeForCompletenessInSeconds: Int
    ): Promise<Pair<CrumbWithId?, Int>> {
        return sleep(waitBetweenSteps) {
            simulateJobFinished(jobExecutionData)
        }.then {
            getFakeCrumbWithIdAndBuildNumber()
        }
    }

    private fun simulateJobFinished(jobExecutionData: JobExecutionData): Promise<Boolean> {
        checkIfNeedsToFail(jobExecutionData)
        return informIfStepWise("job ${jobExecutionData.jobName} ended")
            .then { true }
    }

    private fun checkIfNeedsToFail(jobExecutionData: JobExecutionData) {
        if (isNotPublishJob(jobExecutionData) && count >= failAfterSteps) {
            count = 0
            if (failWithTimeout) {
                throw PollTimeoutException("simulated timeout for ${jobExecutionData.jobName}", "no body")
            } else {
                throw IllegalArgumentException("simulating a failure for ${jobExecutionData.jobName}")
            }
        }
    }

    private fun isNotPublishJob(jobExecutionData: JobExecutionData) =
        !jobExecutionData.jobName.startsWith("publish")

    private fun informIfStepWise(msg: String): Promise<Unit> =
        if (stepWise) showAlert(msg)
        else Promise.resolve(Unit)

    private fun getFakeCrumbWithIdAndBuildNumber() =
        CrumbWithId("Jenkins-Crumb", "onlySimulation") to 100
}
