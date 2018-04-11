package ch.loewenfels.depgraph.gui

import kotlinx.html.HTMLTag
import kotlinx.html.br
import kotlinx.html.dom.append
import kotlinx.html.id
import kotlinx.html.js.div
import kotlinx.html.js.i
import kotlinx.html.js.span

private var msgCounter = 0

fun showStatus(message: String) {
    elementById("status").innerText = message
}

fun showInfo(message: String) {
    showMessageOfType("info", "info_outline", message)
}

fun showWarning(message: String) {
    showMessageOfType("warning", "warning", message)
}

fun showError(message: String) {
    showMessageOfType("error", "error_outline", message)
}

private fun showMessageOfType(type: String, icon: String, message: String) {
    elementById("messages").append {
        div(type) {
            val msgId = "msg${msgCounter++}"
            this.id = msgId
            span("close") {
                val span = getUnderlyingHtmlElement()
                span.addEventListener("click", { elementById(msgId).style.display = "none" })
            }
            i("material-icons") {
                +icon
            }
            div("text") {
                convertNewLinesToBr(message)
            }
        }
    }
}

fun showError(t: Throwable): Nothing {
    val sb = StringBuilder()
    var cause = t.cause
    while(cause != null){
        sb.append("\nCause: ").append(cause)
        cause = cause.cause
    }
    showError("${t::class.js.name}: ${t.message}$sb")
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
