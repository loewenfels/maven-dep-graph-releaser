package ch.loewenfels.depgraph.gui.actions

import ch.loewenfels.depgraph.gui.SECOND
import ch.loewenfels.depgraph.gui.*
import ch.loewenfels.depgraph.gui.components.Messages.Companion.showSuccess
import ch.loewenfels.depgraph.gui.components.Messages.Companion.showWarning
import ch.loewenfels.depgraph.gui.jobexecution.CrumbWithId
import ch.loewenfels.depgraph.gui.jobexecution.JobExecutionData
import ch.loewenfels.depgraph.gui.jobexecution.JobExecutor
import ch.loewenfels.depgraph.gui.jobexecution.toQueryParameters
import ch.loewenfels.depgraph.gui.serialization.ModifiableState
import kotlin.browser.window
import kotlin.js.Promise

class Publisher(
    private val publishJobUrl: String,
    private var modifiableState: ModifiableState
) {

    fun publish(fileName: String, verbose: Boolean, jobExecutor: JobExecutor): Promise<*> {
        changeCursorToProgress()
        val doNothingPromise: (Any?) -> Promise<*> = { Promise.resolve(1) }
        val parameters = mapOf(
            "fileName" to fileName,
            "json" to modifiableState.json
        )
        val jobExecutionData = JobExecutionData.buildWithParameters(
            "publish $fileName.json",
            publishJobUrl,
            toQueryParameters(parameters),
            parameters
        )
        return jobExecutor.trigger(
            jobExecutionData,
            doNothingPromise,
            doNothingPromise,
            pollEverySecond = 2,
            maxWaitingTimeForCompletenessInSeconds = 60,
            verbose = verbose
        ).then { (crumbWithId, buildNumber) ->
            extractResultJsonUrl(jobExecutor, crumbWithId, publishJobUrl, buildNumber).then {
                buildNumber to it
            }
        }.then { (buildNumber, releaseJsonUrl) ->
            changeUrlAndReloadOrAddHint(publishJobUrl, buildNumber, releaseJsonUrl, verbose)
        }.finally {
            changeCursorBackToNormal()
        }
    }

    private fun extractResultJsonUrl(
        jobExecutor: JobExecutor,
        crumbWithId: CrumbWithId?,
        jobUrl: String,
        buildNumber: Int
    ): Promise<String> {
        val xpathUrl = "$jobUrl$buildNumber/api/xml?xpath=//artifact/fileName"
        return jobExecutor.pollAndExtract(
            crumbWithId,
            xpathUrl,
            resultRegex,
            pollEverySecond = 2,
            maxWaitingTimeInSeconds = 20,
            errorHandler = { e ->
                throw IllegalStateException(
                    "Could not find the published release.json as artifact of the publish job." +
                        "\nJob URL: $jobUrl" +
                        "\nRegex used: ${resultRegex.pattern}" +
                        "\nContent: ${e.body}"
                )
            }
        ).then { fileName ->
            "$jobUrl$buildNumber/artifact/$fileName"
        }
    }

    private fun changeUrlAndReloadOrAddHint(
        jobUrl: String,
        buildNumber: Int,
        releaseJsonUrl: String,
        verbose: Boolean
    ) {
        val prefix = window.location.protocol + "//" + window.location.hostname + "/"
        val isOnSameHost = jobUrl.startsWith(prefix)
        if (isOnSameHost) {
            val pipelineUrl = window.location.href.substringBefore('#')
            val relativeJobUrl = jobUrl.substringAfter(prefix)
            val numOfChars = pipelineUrl.substringAfter(prefix).count { it == '/' }
            val relativeJsonUrl = "../".repeat(numOfChars) + releaseJsonUrl.substringAfter(prefix)
            val url = "$pipelineUrl#$relativeJsonUrl${App.PUBLISH_JOB}$relativeJobUrl"
            if (verbose) {
                val successMsg = showSuccess(
                    "Publishing successful, going to change to the new location." +
                        "\nIf this message does not disappear, then it means the switch failed. Please visit the following url manually:" +
                        "\n$url"
                )
                sleep(2 * SECOND) {
                    window.location.href = url
                    successMsg.remove()
                }
            } else {
                window.location.href = url
            }
        } else if (verbose) {
            showWarning(
                "Remote publish server detected. We currently do not support to consume remote release.json." +
                    "\nYou can save changes and it gets published on the remote server, but we will not change the url accordingly. Thus, please do not reload the page after a save because you would load the old state of the release.json" +
                    "\nAlternatively you can download the published release.json from here: $jobUrl$buildNumber and adjust the url manually."
            )
        }
    }

    fun applyChanges(): Boolean = modifiableState.applyChanges()

    companion object {
        private val resultRegex = Regex("<fileName>([^<]+)</fileName>")
    }
}
