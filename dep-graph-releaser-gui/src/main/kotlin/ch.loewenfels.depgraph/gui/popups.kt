package ch.loewenfels.depgraph.gui

import kotlinx.html.HTMLTag
import kotlinx.html.br
import kotlinx.html.dom.append
import kotlinx.html.id
import kotlinx.html.js.div
import kotlinx.html.js.i
import kotlinx.html.js.span
import kotlinx.html.title
import org.w3c.dom.HTMLElement
import kotlin.browser.window

private var msgCounter = 0

fun showStatus(message: String) {
    elementById("status").innerText = message
}
fun showSuccess(message: String, autoCloseAfterMs: Int? = null)
    = showMessageOfType("success", "check_circle", message, autoCloseAfterMs)

fun showInfo(message: String, autoCloseAfterMs: Int? = null)
    = showMessageOfType("info", "info_outline", message, autoCloseAfterMs)

fun showWarning(message: String, autoCloseAfterMs: Int? = null)
    = showMessageOfType("warning", "warning", message, autoCloseAfterMs)

fun showError(message: String) = showMessageOfType("error", "error_outline", message, null)

private fun showMessageOfType(type: String, icon: String, message: String, autoCloseAfterMs: Int?): HTMLElement {
    lateinit var element: HTMLElement
    elementById("messages").append {
        div(type) {
            val msgId = "msg${msgCounter++}"
            this.id = msgId
            span("close") {
                title = "close this message"
                val span = getUnderlyingHtmlElement()
                span.addEventListener("click", { closeMessage(msgId) })
            }
            i("material-icons") {
                +icon
            }
            div("text") {
                convertNewLinesToBr(message)
            }
            if (autoCloseAfterMs != null) {
                window.setTimeout({ closeMessage(msgId) }, autoCloseAfterMs)
            }
            element = getUnderlyingHtmlElement()
        }
    }
    return element
}

private fun closeMessage(msgId: String) {
    elementById(msgId).style.display = "none"
}

fun showThrowableAndThrow(t: Throwable): Nothing {
    val sb = StringBuilder()
    var cause = t.cause
    while (cause != null) {
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
