package ch.loewenfels.depgraph.gui.jobexecution

import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

class RestApiBasedQueuedItemUrlExtractor(
    private val identifyingParams: Map<String, String>
) : QueuedItemUrlExtractor {

    override fun extract(
        usernameToken: UsernameToken,
        crumbWithId: CrumbWithId?,
        response: Response,
        jobExecutionData: JobExecutionData
    ): Promise<String?> {
        // We somehow have to get the build number. Unfortunately it is not returned by M2Plugin from the POST request
        // (also not in header) that is why we browse the Jenkins queue and search for a match
        // based on the given identifyingParams.
        val jenkinsBaseUrl = jobExecutionData.jobBaseUrl.substringBefore("/job/")
        val t = jobExecutionData.jobBaseUrl.substringAfter("/job/")
        val jobName = if (t.endsWith("/")) t.substringBeforeLast("/") else t
        val headers = createHeaderWithAuthAndCrumb(usernameToken, crumbWithId)
        val init = createRequestInit(null, RequestVerb.GET, headers)
        val paramsIdentification = createParameterRegexPattern(identifyingParams)

        return window.fetch("$jenkinsBaseUrl/queue/api/xml", init)
            .then(::checkStatusOk)
            .then { body ->
                val queuedItemRegex = Regex(
                    "<item>[\\S\\s]*?" +
                        paramsIdentification +
                        "<task>[\\S\\s]*?<name>$jobName</name>[\\S\\s]*?</task>[\\S\\s]*?" +
                        "<url>([^<]+)</url>[\\S\\s]*?" +
                        "</item>"
                )
                val matchResult = queuedItemRegex.find(body)
                if (matchResult != null) {
                    jenkinsBaseUrl + "/" + matchResult.groupValues[1]
                } else {
                    null
                }
            }
    }
}
