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
            convertNewLinesToBrTabToTwoSpacesAndParseUrls(message)
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
    elementByIdOrNull<HTMLElement>(msgId)?.remove()
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

private fun StringBuilder.appendThrowable(t: Throwable): StringBuilder {
    val nullableStack: String? = t.asDynamic().stack as? String
    return if (nullableStack != null) {
        val stackWithHead: String = nullableStack
        val firstNewLine = stackWithHead.indexOf('\n')
        val stack = if (firstNewLine >= 0) {
            append(stackWithHead.substring(0, firstNewLine + 1)).append('\n')
            stackWithHead.substring(firstNewLine + 1)
        } else {
            stackWithHead
        }
        append(stack.replace("   ", "\t"))
    } else {
        append("${t::class.js.name}: ${t.message}")
    }
}

private fun DIV.convertNewLinesToBrTabToTwoSpacesAndParseUrls(message: String) {
    if (message.isEmpty()) return

    val messages = message.split("\n")
    convertTabToTwoSpacesAndUrlToLinks(messages[0])
    for (i in 1 until messages.size) {
        br
        convertTabToTwoSpacesAndUrlToLinks(messages[i])
    }
}

private fun DIV.convertTabToTwoSpacesAndUrlToLinks(message: String) {
    var matchResult = urlRegex.find(message)
    if (matchResult != null) {
        var index = 0
        do {
            val match = matchResult!!
            convertTabToTwoSpaces(message.substring(index, match.range.start))
            a(href = match.value) {
                +match.value
            }
            index = match.range.endInclusive + 1
            matchResult = match.next()
        } while (matchResult != null)
        convertTabToTwoSpaces(message.substring(index))
    } else {
        convertTabToTwoSpaces(message)
    }
}

private fun DIV.convertTabToTwoSpaces(content: String) {
    var currentIndex = 0
    do {
        val index = content.indexOf('\t', currentIndex)
        if (index < 0) break

        +content.substring(currentIndex, index)
        unsafe { +"&nbsp;&nbsp;" }
        currentIndex = index + 1
    } while (true)
    +content.substring(currentIndex)
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

fun showOutput(title: String, output: String): Promise<Unit> {
    return Promise { resolve, _ ->
        showModal(
            {
                div("output") {
                    i("material-icons") { +"list_alt" }; span { +title }
                    textArea { +output }
                }
            },
            { box -> modalButton("OK", box, resolve, Unit) }
        )
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

private fun showModal(
    msg: String,
    buttonCreator: DIV.(HTMLElement) -> Unit
) = showModal({ i("material-icons") { +"help_outline" }; span { +msg } }, buttonCreator)

private fun showModal(
    contentCreator: DIV.() -> Unit,
    buttonCreator: DIV.(HTMLElement) -> Unit
) {
    val modals = elementById("modals")
    modals.append {
        div("box") {
            val box = getUnderlyingHtmlElement()
            div("text") {
                contentCreator()
            }
            div("buttons") {
                buttonCreator(box)
            }
            box.style.visibility = "hidden"
        }
    }
    val box = modals.lastChild as HTMLElement
    val top = window.innerHeight / 2.5 - box.offsetHeight / 2
    val left = window.innerWidth / 2 - box.offsetWidth / 2
    box.style.top = "${top}px"
    box.style.left = "${left}px"
    box.style.visibility = "visible"
}
