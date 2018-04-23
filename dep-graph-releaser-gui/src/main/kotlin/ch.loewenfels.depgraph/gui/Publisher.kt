package ch.loewenfels.depgraph.gui

import kotlin.browser.window
import kotlin.js.Promise

class Publisher(
    jenkinsUrl: String,
    usernameToken: UsernameToken,
    private val publishJobUrl: String,
    private var modifiableJson: ModifiableJson
) {
    private val jobExecutor = JobExecutor(jenkinsUrl, usernameToken)

    fun publish(fileName: String, verbose: Boolean): Promise<*> {
        changeCursorToProgress()
        val body = "fileName=$fileName&json=${modifiableJson.json}"
        return jobExecutor.trigger(
            publishJobUrl,
            "publish release-$fileName.json",
            body,
            verbose,
            { Promise.resolve(1) }
        ).then { (crumbWithId, buildNumber) ->
            extractResultJsonUrl(crumbWithId, publishJobUrl, buildNumber).then {
                buildNumber to it
            }
        }.then { (buildNumber, releaseJsonUrl) ->
            changeUrlAndReloadOrAddHint(publishJobUrl, buildNumber, releaseJsonUrl, verbose)
        }.finally {
            changeCursorBackToNormal()
        }
    }

    private fun extractResultJsonUrl(
        crumbWithId: CrumbWithId?,
        jobUrl: String,
        buildNumber: Int
    ): Promise<String> {
        val xpathUrl = "$jobUrl$buildNumber/api/xml?xpath=//artifact/fileName"
        return jobExecutor.pollAndExtract(crumbWithId, xpathUrl, resultRegex) { e ->
            throw IllegalStateException(
                "Could not find the published release.json as artifact." +
                    "\nJob URL: $jobUrl" +
                    "\nRegex used: ${resultRegex.pattern}" +
                    "\nContent: ${e.body}"
            )
        }.then { fileName ->
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
            val url = "$pipelineUrl#$releaseJsonUrl${App.PUBLISH_JOB}$relativeJobUrl"
            if (verbose) {
                val successMsg = showSuccess(
                    "Publishing successful, going to change to the new location." +
                        "\nIf this message does not disappear, then it means the switch failed. Please visit the following url manually:" +
                        "\n$url"
                )
                sleep(2000) {
                    window.location.href = url
                    successMsg.style.display = "none"
                }
            } else {
                window.location.href = url
            }
        } else if (verbose) {
            showWarning(
                "Remote publish server detected. We currently do not support to consume remote release.json." +
                    "\nYou can save changes and it gets published on the remote server, but we will not change the url accordingly." +
                    "\nThus, please do not reload the page after a save because you would load the old state of the release.json" +
                    "\nAlternatively you can download the published release.json from here: $jobUrl$buildNumber" +
                    "\nand adjust the url manually."
            )
        }
    }

    fun applyChanges(): Boolean {
        return modifiableJson.applyChanges()
    }

    companion object {
        private val resultRegex = Regex("<fileName>([^<]+)</fileName>")
    }
}
