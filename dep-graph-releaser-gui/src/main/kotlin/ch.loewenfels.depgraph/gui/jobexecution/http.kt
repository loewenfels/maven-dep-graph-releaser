package ch.loewenfels.depgraph.gui.jobexecution

import org.w3c.fetch.*
import kotlin.browser.window
import kotlin.js.Promise

fun checkStatusOk(response: Response): Promise<Pair<Response, String>> {
    @Suppress("UNCHECKED_CAST" /* is non-null string because we do not ignore an error code */)
    return checkResponseIgnore(response, null) as Promise<Pair<Response, String>>
}

fun checkStatusOkOr403(response: Response) = checkResponseIgnore(response, 403)
fun checkStatusOkOr404(response: Response) = checkResponseIgnore(response, 404)

private fun checkResponseIgnore(response: Response, ignoringError: Int?): Promise<Pair<Response, String?>> {
    return response.text().then { text ->
        if (ignoringError != null && ignoringError.toShort() == response.status) {
            response to null
        } else {
            check(response.ok) { "response was not ok, ${response.status}: ${response.statusText}\n$text" }
            response to text
        }
    }
}


@Suppress("UnsafeCastFromDynamic")
fun createFetchInitWithCredentials(): RequestInit {
    val init = js("({})")
    init.credentials = "include"
    init.method = RequestVerb.GET
    return init
}


fun createHeaderWithAuthAndCrumb(authData: AuthData): dynamic {
    val headers = js("({})")
    addAuthentication(headers, authData.usernameAndApiToken)
    if (authData.crumbWithId != null) {
        headers[authData.crumbWithId.id] = authData.crumbWithId.crumb
    }
    return headers
}

fun addAuthentication(headers: dynamic, usernameAndApiToken: UsernameAndApiToken) {
    val base64UsernameAndToken = window.btoa("${usernameAndApiToken.username}:${usernameAndApiToken.token}")
    headers["Authorization"] = "Basic $base64UsernameAndToken"
}

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
external interface RequestVerb {
    companion object
}

inline val RequestVerb.Companion.GET get() = "GET".asDynamic().unsafeCast<RequestVerb>()
inline val RequestVerb.Companion.POST get() = "POST".asDynamic().unsafeCast<RequestVerb>()

fun createGetRequest(headers: dynamic): RequestInit = createRequestInit(null, RequestVerb.GET, headers)

fun createRequestInit(
    body: String?,
    method: RequestVerb,
    headers: dynamic
): RequestInit {
    val init = RequestInit(
        body = body,
        method = method.unsafeCast<String>(),
        headers = headers,
        mode = RequestMode.CORS,
        cache = org.w3c.fetch.RequestCache.NO_CACHE,
        redirect = org.w3c.fetch.RequestRedirect.FOLLOW,
        credentials = org.w3c.fetch.RequestCredentials.INCLUDE,
        referrer = "no-referrer",
        referrerPolicy = "no-referrer"
    )
    //have to remove properties because RequestInit sets them to null which is not what we want/is not valid
    js(
        "delete init.integrity;" +
            "delete init.keepalive;" +
            "delete init.window;"
    )
    return init
}
