package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.gui.showThrowable
import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

object UsernameTokenRegistry {

    private val fullNameRegex = Regex("<input[^>]+name=\"_\\.fullName\"[^>]+value=\"([^\"]+)\"")
    private val apiTokenRegex = Regex("<input[^>]+name=\"_\\.apiToken\"[^>]+value=\"([^\"]+)\"")
    private val usernameRegex = Regex("<a[^>]+href=\"[^\"]*/user/([^\"]+)\"")

    private val usernameTokens = hashMapOf<String, UsernameAndApiToken>()

    fun forHostOrThrow(jenkinsBaseUrl: String): UsernameAndApiToken
        = forHost(jenkinsBaseUrl) ?: throw IllegalStateException("could not find usernameAndApiToken for $jenkinsBaseUrl")

    fun forHost(jenkinsBaseUrl: String): UsernameAndApiToken? = usernameTokens[urlWithoutEndingSlash(jenkinsBaseUrl)]

    private fun urlWithoutEndingSlash(jenkinsBaseUrl: String): String {
        return if (jenkinsBaseUrl.endsWith("/")) {
            jenkinsBaseUrl.substring(0, jenkinsBaseUrl.length - 1)
        } else {
            jenkinsBaseUrl
        }
    }


    /**
     * Retrieves the API token of the logged in user at [jenkinsBaseUrl] and registers it, moreover it returns the name
     * of the user in the same request (the name is not stored though)
     *
     * @return A pair consisting of the name and the [UsernameAndApiToken] of the logged in user.
     */
    fun register(jenkinsBaseUrl: String): Promise<Pair<String, UsernameAndApiToken>?>
        = retrieveUserAndApiTokenAndSaveToken(jenkinsBaseUrl)

    private fun retrieveUserAndApiTokenAndSaveToken(jenkinsBaseUrl: String): Promise<Pair<String, UsernameAndApiToken>?> {
        val urlWithoutSlash = urlWithoutEndingSlash(jenkinsBaseUrl)
        return window.fetch("$urlWithoutSlash/me/configure", createFetchInitWithCredentials())
            .then(::checkStatusOkOr403)
            .catch<Pair<Response, String?>?> { t ->
                showThrowable(Error("Could not retrieve user and API token for $urlWithoutSlash", t))
                null
            }
            .then { pair ->
                val body = pair?.second
                if (body == null) {
                    null
                } else {
                    val (username, name, apiToken) = extractNameAndApiToken(body)
                    val usernameToken = UsernameAndApiToken(username, apiToken)
                    usernameTokens[urlWithoutSlash] = usernameToken
                    name to usernameToken
                }
            }
    }


    private fun extractNameAndApiToken(body: String): Triple<String, String, String> {
        val usernameMatch = usernameRegex.find(body) ?: throw IllegalStateException("Could not find username")
        val fullNameMatch = fullNameRegex.find(body) ?: throw IllegalStateException("Could not find user's name")
        val apiTokenMatch = apiTokenRegex.find(body) ?: throw IllegalStateException("Could not find API token")
        return Triple(usernameMatch.groupValues[1], fullNameMatch.groupValues[1], apiTokenMatch.groupValues[1])
    }
}
