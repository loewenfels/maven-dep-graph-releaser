package ch.loewenfels.depgraph.gui.jobexecution

import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

fun issueCrumb(
    jenkinsBaseUrl: String,
    usernameAndApiToken: UsernameAndApiToken
): Promise<AuthData> {
    val url = "$jenkinsBaseUrl/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)"
    val headers = createHeaderWithAuthAndCrumb(AuthData(usernameAndApiToken, null))
    val init = createGetRequest(headers)
    return window.fetch(url, init)
        .then(::checkStatusOkOr404)
        .catch<Pair<Response, String?>> {
            throw Error("Cannot issue a crumb", it)
        }.then { (_, crumbWithIdString) ->
            val crumbWithId = if (crumbWithIdString != null) {
                val (id, crumb) = crumbWithIdString.split(':')
                CrumbWithId(id, crumb)
            } else {
                null
            }
            AuthData(usernameAndApiToken, crumbWithId)
        }
}

