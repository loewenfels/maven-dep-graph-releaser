package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.gui.jobexecution.BuilderNumberExtractor.Companion.numberRegex
import ch.loewenfels.depgraph.gui.sleep
import kotlin.js.Promise

class QueuedItemBasedBuildNumberExtractor(
    private val authData: AuthData,
    private val queuedItemUrl: String
) : BuilderNumberExtractor {

    override fun extract(): Promise<Int> {
        // wait a bit, if we are too fast we run almost certainly into a 404 (job is not even queued)
        return sleep(200) {
            Poller.pollAndExtract(
                authData,
                "${queuedItemUrl}api/xml",
                numberRegex,
                pollEverySecond = 2,
                maxWaitingTimeInSeconds = 20,
                errorHandler = { e ->
                    throw PollTimeoutException(
                        "Extracting the build number via the queued item failed (max waiting time reached). Could not find the build number in the returned body." +
                            "\nJob URL: $queuedItemUrl" +
                            "\nRegex used: ${numberRegex.pattern}"
                    , e.body, e)
                })
        }.then { it.toInt() }
    }
}
