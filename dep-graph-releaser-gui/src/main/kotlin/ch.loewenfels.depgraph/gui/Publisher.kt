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

    fun publish(fileName: String): Promise<Boolean> {
        changeCursorToProgress()
        val body = "fileName=$fileName&json=${modifiableJson.json}"
        return jobExecutor.trigger(publishJobUrl, "publish release-$fileName.json", body)
            .then { (crumbWithId, buildNumber) ->
                extractResultJsonUrl(crumbWithId, publishJobUrl, buildNumber).then{
                    buildNumber to it
                }
            }.then { (buildNumber, releaseJsonUrl) ->
                changeUrlAndReloadOrAddHint(publishJobUrl, buildNumber, releaseJsonUrl)
            }.finally { it: Any? ->
                changeCursorBackToNormal()
                it != null
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

    private fun changeUrlAndReloadOrAddHint(jobUrl: String, buildNumber: Int, releaseJsonUrl: String) {
        val prefix = window.location.protocol + "//" + window.location.hostname + "/"
        val isOnSameHost = jobUrl.startsWith(prefix)
        if (isOnSameHost) {
            val pipelineUrl = window.location.href.substringBefore('#')
            val relativeJobUrl = jobUrl.substringAfter(prefix)
            val url = "$pipelineUrl#$releaseJsonUrl${App.PUBLISH_JOB}$relativeJobUrl"
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
            showWarning(
                "The release.json was successfully published. " +
                    "However, since it is not on the same server, we cannot consume it." +
                    "\nVisit the publish job for further information: $jobUrl$buildNumber"
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
