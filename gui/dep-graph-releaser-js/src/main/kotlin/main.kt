import ch.loewenfels.depgraph.Gui
import ch.loewenfels.depgraph.Toggler
import ch.loewenfels.depgraph.deserialize
import ch.loewenfels.depgraph.display
import org.w3c.fetch.Request
import kotlin.browser.document
import kotlin.browser.window
import kotlin.js.Promise

@JsName("main")
fun main(jsonUrl: String) {
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
            addError(it)
        }
}

private fun loadJson(jsonUrl: String): Promise<Any> {
    return window.fetch(Request(jsonUrl))
        .then { response ->
            response.text().then { text ->
                require(response.ok) {
                    "response was not ok, ${response.status}: ${response.statusText}<br/>$text"
                }
                text
            }
        }
}

private fun switchLoaderAndGui() {
    display("loader", "none")
    display("gui", "block")
}

private fun addError(t: Throwable) {
    document.getElementById("messages")!!.innerHTML += """
        |<div class="error">${t::class.js.name}: ${t.message}
        |${if (t.cause != null) "<div class=\"cause\">Cause: ${t.cause}</div>" else ""}
        |</div>
        |
    """.trimMargin()
    throw t //this way it also shows up in console with stacktrace
}
