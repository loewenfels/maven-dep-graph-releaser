package ch.loewenfels.depgraph.gui.jobexecution

import org.w3c.fetch.Response
import kotlin.js.Promise

object LocationBasedQueuedItemUrlExtractor : QueuedItemUrlExtractor {

    /**
     * Takes the QueuedItemUrl from the Location header of the [response].
     */
    override fun extract(authData: AuthData, response: Response, jobExecutionData: JobExecutionData): Promise<String> {
        return Promise.resolve(
            response.headers.get("Location") ?: throw IllegalStateException(
                "Job ${jobExecutionData.jobName} queued but Location header not found in response of Jenkins." +
                    "\nHave you exposed Location with Access-Control-Expose-Headers?"
            )
        )
    }
}
