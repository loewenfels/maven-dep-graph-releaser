package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.gui.sleep
import kotlin.js.Promise

class QueuedItemBasedBuildNumberExtractor(
    private val authData: AuthData,
    private val queuedItemUrl: String
) : BuilderNumberExtractor {

    override fun extract(): Promise<Int> {
        val xpathUrl = "${queuedItemUrl}api/xml?xpath=//executable/number"

        // wait a bit, if we are too fast we run almost certainly into a 404 (job is not even queued)
        return sleep(200) {
            Poller.pollAndExtract(authData, xpathUrl, numberRegex) { e ->
                throw IllegalStateException(
                    "Could not find the build number in the returned body." +
                        "\nJob URL: $queuedItemUrl" +
                        "\nRegex used: ${numberRegex.pattern}" +
                        "\nContent: ${e.body}"
                )
            }
        }.then { it.toInt() }
    }

    companion object {
        private val numberRegex = Regex("<number>([0-9]+)</number>")
    }
}
