import ch.loewenfels.depgraph.*
import org.w3c.fetch.Request
import kotlin.browser.window
import kotlin.js.Promise

@JsName("toggler")
lateinit var toggler: Toggler

@JsName("main")
fun main(jsonUrl: String) {
    loadJson(jsonUrl)
        .catch {
            throw Error("Could not load json.", it)
        }
        .then { body: String ->
            val releasePlan = deserialize(body)
            Gui(releasePlan).load()
            toggler = Toggler()
            switchLoaderAndGui()
        }
        .catch {
            showError(it)
        }
}

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
