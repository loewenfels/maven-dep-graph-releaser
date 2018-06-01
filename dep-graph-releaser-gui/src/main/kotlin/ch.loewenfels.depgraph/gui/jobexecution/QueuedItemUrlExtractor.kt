package ch.loewenfels.depgraph.gui.jobexecution

import org.w3c.fetch.Response
import kotlin.js.Promise

interface QueuedItemUrlExtractor {

    /**
     * Extracts the queued item id for a triggered job.
     *
     * @param response from the Trigger-POST-Request
     * @param jobExecutionData which was used to perform the Trigger-POST-Request.
     */
    fun extract(
        usernameToken: UsernameToken,
        crumbWithId: CrumbWithId?,
        response: Response,
        jobExecutionData: JobExecutionData
    ): Promise<String?>
}
