import ch.loewenfels.depgraph.*
import org.w3c.fetch.Request
import kotlin.browser.window
import kotlin.js.Promise

@JsName("main")
fun main() {
    val jsonUrl = if(window.location.hash != "") {
        window.location.hash.substring(1)
    } else {
        "./release.json"
    }
    loadJson(jsonUrl)
        .catch {
            throw Error("Could not load json.", it)
        }
        .then { body: String ->
            val releasePlan = deserialize(body)
            Gui(releasePlan).load()
            switchLoaderAndGui()
        }
        .catch {
            showError(it)
        }
}
val onlyUsedToCallMain = main()

private fun loadJson(jsonUrl: String): Promise<Any> {
    return window.fetch(Request(jsonUrl))
        .then { response ->
            response.text().then { text ->
                require(response.ok) {
                    "response was not ok, ${response.status}: ${response.statusText}\n$text"
                }
                text
            }
        }
}

private fun switchLoaderAndGui() {
    display("loader", "none")
    display("gui", "table")
}
