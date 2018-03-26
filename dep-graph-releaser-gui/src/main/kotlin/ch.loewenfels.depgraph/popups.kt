package ch.loewenfels.depgraph

import kotlinx.html.HTMLTag
import kotlinx.html.br
import kotlinx.html.dom.append
import kotlinx.html.id
import kotlinx.html.js.div
import kotlinx.html.js.span
import kotlin.browser.document

private var msgCounter = 0

fun showMessage(message: String) {
    showMessageOfType("msg", message, withClose = false)
}

fun showInfo(message: String) {
    showMessageOfType("info", message, withClose = true)
}

fun showWarning(message: String) {
    showMessageOfType("warning", message, withClose = true)
}

fun showError(message: String) {
    showMessageOfType("error", message, withClose = true)
}

private fun showMessageOfType(type: String, message: String, withClose: Boolean) {
    document.getElementById("messages")!!.append {
        div(type) {
            val msgId = "msg${msgCounter++}"
            id = msgId
            if (withClose) {
                span("close") {
                    val span = getUnderlyingHtmlElement()
                    span.addEventListener("click", { elementById(msgId).style.display = "none" })
                }
            }
            convertNewLinesToBr(message)
        }
    }
}

fun showError(t: Throwable) {
    showError("${t::class.js.name}: ${t.message}" +
        if (t.cause != null) "\nCause: ${t.cause}" else ""
    )
    throw t //this way it also shows up in console with stacktrace
}

private fun HTMLTag.convertNewLinesToBr(message: String) {
    if (message.isEmpty()) return

    val messages = message.split("\n")
    +messages[0]
    for (i in 1 until messages.size) {
        br
        +messages[i]
    }
}
