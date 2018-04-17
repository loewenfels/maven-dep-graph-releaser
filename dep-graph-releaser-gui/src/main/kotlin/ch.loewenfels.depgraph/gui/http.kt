package ch.loewenfels.depgraph.gui

import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

fun checkStatusOk(response: Response): Promise<String> {
    @Suppress("UNCHECKED_CAST" /* is non-null string because we do not ignore an error code */)
    return checkResponseIgnore(response, null) as Promise<String>
}

fun checkStatusOkOr403(response: Response) = checkResponseIgnore(response, 403)
fun checkStatusOkOr404(response: Response) = checkResponseIgnore(response, 404)

private fun checkResponseIgnore(response: Response, ignoringError: Int?): Promise<String?> {
    return response.text().then { text ->
        if (ignoringError != null && ignoringError.toShort() == response.status) {
            null
        } else {
            check(response.ok) { "response was not ok, ${response.status}: ${response.statusText}\n$text" }
            text
        }
    }
}


@Suppress("UnsafeCastFromDynamic")
fun createFetchInitWithCredentials() : RequestInit {
    val init = js("({})")
    init.credentials = "include"
    return init
}

fun addAuthentication(headers: dynamic, usernameToken: UsernameToken) {
    val base64UsernameAndToken = window.btoa("${usernameToken.username}:${usernameToken.token}")
    headers["Authorization"] = "Basic $base64UsernameAndToken"
}
