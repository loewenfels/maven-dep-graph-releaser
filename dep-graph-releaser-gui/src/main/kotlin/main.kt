import ch.loewenfels.depgraph.gui.*
import org.w3c.fetch.Request
import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

@JsName("main")
fun main() {
    window.onload = {
        val jsonUrl = determineJsonUrl()
        loadJson(jsonUrl)
            .then(::checkStatus)
            .catch {
                throw Error("Could not load json.", it)
            }
            .then { body: String ->
                val publishJob = determinePublishJob()
                val releasePlan = deserialize(body)
                val menu = Menu(body, publishJob)
                Gui(releasePlan, menu).load()
                switchLoaderAndGui()
            }
            .catch {
                showError(it)
            }
    }
}

fun determinePublishJob(): String? {
    return if (window.location.hash.contains(PUBLISH_JOB)) {
        window.location.hash.substringAfter(PUBLISH_JOB)
    } else {
        null
    }
}

private fun determineJsonUrl(): String {
    return if (window.location.hash != "") {
        window.location.hash.substring(1).substringBefore("&")
    } else {
        showError(IllegalStateException("You need to specify a release.json." +
            "\nAppend the path with preceding # to the url, e.g., ${window.location}#release.json"))
    }
}

@Suppress("unused")
val onlyUsedToCallMain = main()

private fun loadJson(jsonUrl: String): Promise<Response> {
    return window.fetch(Request(jsonUrl))
}

fun checkStatus(response: Response): Promise<String> {
    return response.text().then { text ->
        check(response.ok) {
            "response was not ok, ${response.status}: ${response.statusText}\n$text"
        }
        text
    }
}

private fun switchLoaderAndGui() {
    display("loader", "none")
    display("gui", "block")
}

const val PUBLISH_JOB = "&publishJob="
