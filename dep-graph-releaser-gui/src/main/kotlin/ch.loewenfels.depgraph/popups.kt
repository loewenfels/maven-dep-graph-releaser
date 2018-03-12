package ch.loewenfels.depgraph

import kotlinx.html.dom.append
import kotlinx.html.js.div
import kotlin.browser.document

fun showError(t: Throwable) {
    document.getElementById("messages")!!.append {
        div("error") {
            +"${t::class.js.name}: ${t.message}"
            if (t.cause != null) {
                div("cause") {
                    +"Cause: ${t.cause}"
                }
            }
        }
    }
    throw t //this way it also shows up in console with stacktrace
}

fun showMessage(msg: String) {
    document.getElementById("messages")!!.append {
        div("msg") { +msg }
    }
}
