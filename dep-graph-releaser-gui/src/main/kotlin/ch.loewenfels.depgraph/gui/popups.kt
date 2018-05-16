package ch.loewenfels.depgraph.gui

import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement
import kotlin.browser.document
import kotlin.browser.window
import kotlin.js.Promise

private var msgCounter = 0

fun showStatus(message: String) {
    elementById("status").innerText = message
}

fun showSuccess(message: String, autoCloseAfterMs: Int? = null) =
    showMessageOfType("success", "check_circle", message, autoCloseAfterMs)

fun showInfo(message: String, autoCloseAfterMs: Int? = null) =
    showMessageOfType("info", "info_outline", message, autoCloseAfterMs)

fun showWarning(message: String, autoCloseAfterMs: Int? = null) =
    showMessageOfType("warning", "warning", message, autoCloseAfterMs)

fun showError(message: String) = showMessageOfType("error", "error_outline", message, null)

private fun showMessageOfType(type: String, icon: String, message: String, autoCloseAfterMs: Int?): HTMLElement {
    val messages = elementById("messages")
    val div = document.create.div(type) {
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
            convertNewLinesToBrAndParseUrls(message)
        }
        if (autoCloseAfterMs != null) {
            window.setTimeout({ closeMessage(msgId) }, autoCloseAfterMs)
        }
    }
    val hideMessagesButton = elementById(Gui.HIDE_MESSAGES_HTML_ID)
    messages.insertBefore(div, hideMessagesButton.nextSibling)
    return div
}

private fun closeMessage(msgId: String) {
    elementById(msgId).remove()
}

fun showThrowableAndThrow(t: Throwable): Nothing {
    showThrowable(t)
    throw t
}

fun showThrowable(t: Throwable) {
    showError(turnThrowableIntoMessage(t))
}

fun turnThrowableIntoMessage(t: Throwable): String {
    val sb = StringBuilder()
    sb.appendThrowable(t)

    var cause = t.cause
    while (cause != null) {
        sb.append("\n\nCause: ").appendThrowable(cause)
        cause = cause.cause
    }
    return sb.toString()
}

private fun StringBuilder.appendThrowable(t: Throwable) {
    val stack: String? = t.asDynamic().stack as? String
    if (stack != null) {
        append(stack)
    } else {
        append("${t::class.js.name}: ${t.message}")
    }
}

private fun DIV.convertNewLinesToBrAndParseUrls(message: String) {
    if (message.isEmpty()) return

    val messages = message.split("\n")
    convertUrlToLinks(messages[0])
    for (i in 1 until messages.size) {
        br
        convertUrlToLinks(messages[i])
    }
}

private fun DIV.convertUrlToLinks(message: String) {
    var matchResult = urlRegex.find(message)
    if (matchResult != null) {
        var index = 0
        do {
            val match = matchResult!!
            +message.substring(index, match.range.start)
            a(href = match.value) {
                +match.value
            }
            index = match.range.endInclusive + 1
            matchResult = match.next()
        } while (matchResult != null)
        +message.substring(index, message.length)
    } else {
        +message
    }
}

private val urlRegex = Regex("http(?:s)://[^ ]+")


fun showDialog(msg: String): Promise<Boolean> {
    return Promise { resolve, _ ->
        showModal(msg) { box ->
            modalButton("Yes", box, resolve, true)
            modalButton("No", box, resolve, false)
        }
    }
}

fun showAlert(msg: String): Promise<Unit> {
    return Promise { resolve, _ ->
        showModal(msg) { box ->
            modalButton("OK", box, resolve, Unit)
        }
    }
}

private fun <T> DIV.modalButton(buttonText: String, box: HTMLElement, resolve: (T) -> Unit, objectToResolve: T) {
    span {
        +buttonText
        getUnderlyingHtmlElement().addClickEventListener(options = js("{once: true}")) {
            box.remove()
            resolve(objectToResolve)
        }
    }
}

private fun showModal(msg: String, buttonCreator: DIV.(HTMLElement) -> Unit) {
    val modals = elementById("modals")
    modals.append {
        div("box") {
            val box = getUnderlyingHtmlElement()
            div("text") {
                i("material-icons") { +"help_outline" }
                span { +msg }
            }
            div("buttons") {
                buttonCreator(box)
            }
            box.style.visibility = "hidden"
        }
    }
    val box = modals.lastChild as HTMLElement
    val top = document.body!!.clientHeight / 3
    val left = document.body!!.clientWidth / 2 - box.offsetWidth / 2
    box.style.top = "${top}px"
    box.style.left = "${left}px"
    box.style.visibility = "visible"
}
