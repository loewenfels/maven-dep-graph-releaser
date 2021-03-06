package ch.loewenfels.depgraph.gui.components

import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.TypeOfRun
import ch.loewenfels.depgraph.data.toProcessName
import ch.loewenfels.depgraph.gui.*
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import kotlin.browser.document
import kotlin.browser.window
import kotlin.js.Date

class Messages(releasePlan: ReleasePlan) {

    init {
        releasePlan.warnings.forEach { showWarning(it) }
        setInfoBubble(releasePlan.infos)
    }

    private fun setInfoBubble(messages: List<String>) {
        if (messages.isNotEmpty()) {
            val minimized = elementById("infosMinimized")
            minimized.style.display = "block"
            minimized.addEventListener("click", {
                minimized.style.display = "none"
                messages.forEach { showInfo(it) }
            })
        }
        val messagesDiv = elementById(MESSAGES_ID)
        elementById(ContentContainer.HIDE_MESSAGES_HTML_ID).addClickEventListener {
            document.querySelectorAll("#$MESSAGES_ID > div")
                .asList()
                .forEach { messagesDiv.removeChild(it) }
        }
    }

    companion object {
        private const val MESSAGES_ID = "messages"
        private const val HOLDER_CSS_CLASS = "holder"

        fun putMessagesInHolder(lastProcess: TypeOfRun) {
            val messagesDiv = elementById(MESSAGES_ID)
            val messages = getMessages(messagesDiv)
            if (messages.isNotEmpty()) {
                val holderId = nextMessageId()
                val timestamp = (messages[0].childNodes.asList()
                    .find { it is HTMLDivElement && it.className == "timestamp" } as HTMLDivElement).innerText

                val holder = document.create.div(HOLDER_CSS_CLASS) {
                    id = holderId
                    appendClose(holderId)
                    +"Click here to see messages of a previous '${lastProcess.toProcessName()}' process - last message with timestamp $timestamp"
                    title = "Click to see the messages of a previous process."
                    getUnderlyingHtmlElement().addClickOnceEventListener {
                        val holder = elementById(holderId)
                        getMessages(holder).forEach { messagesDiv.insertBefore(it, holder) }
                        messagesDiv.removeChild(holder)
                    }
                }
                holder.className = HOLDER_CSS_CLASS
                messagesDiv.insertBefore(holder, messages[0])
                messages.forEach { holder.appendChild(it) }
            }
        }

        private fun getMessages(node: HTMLElement) =
            node.childNodes.asList().filter { it is HTMLDivElement && it.className != HOLDER_CSS_CLASS }

        fun showSuccess(message: String, autoCloseAfterMs: Int? = null) =
            showMessageOfType("success", "check_circle", message, autoCloseAfterMs)

        fun showInfo(message: String, autoCloseAfterMs: Int? = null) =
            showMessageOfType("info", "info_outline", message, autoCloseAfterMs)

        fun showWarning(message: String, autoCloseAfterMs: Int? = null) =
            showMessageOfType("warning", "warning", message, autoCloseAfterMs)

        fun showError(message: String) = showMessageOfType("error", "error_outline", message, null)

        private var msgCounter = 0
        private fun showMessageOfType(
            type: String,
            icon: String,
            message: String,
            autoCloseAfterMs: Int?
        ): HTMLElement {
            val messages = elementById(MESSAGES_ID)
            val div = document.create.div(type) {
                val msgId = nextMessageId()
                this.id = msgId
                appendClose(msgId)
                div("timestamp") {
                    val now = Date()
                    val year = now.getFullYear().toString().substring(2)
                    val month = padWithZero(now.getMonth() + 1)
                    val day = padWithZero(now.getDay())
                    val hours = now.getHours()
                    val minutes = padWithZero(now.getMinutes())
                    val seconds = padWithZero(now.getSeconds())
                    +"$year-$month-$day $hours:$minutes:$seconds"
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
            val hideMessagesButton = elementById(ContentContainer.HIDE_MESSAGES_HTML_ID)
            messages.insertBefore(div, hideMessagesButton.nextSibling)
            return div
        }

        private fun nextMessageId() = "msg${msgCounter++}"

        private fun DIV.appendClose(msgId: String) {
            span("close") {
                title = "close this message"
                val span = getUnderlyingHtmlElement()
                span.addEventListener("click", { closeMessage(msgId) })
            }
        }

        private fun padWithZero(int: Int) = int.toString().padStart(2, '0')

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

        private fun turnThrowableIntoMessage(t: Throwable): String {
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
                val stackWithMessage: String = getStackWithMessage(t, nullableStack)
                val firstNewLine = stackWithMessage.indexOf("   ")
                val stack = if (firstNewLine >= 0) {
                    append(stackWithMessage.substring(0, firstNewLine)).append('\n')
                    stackWithMessage.substring(firstNewLine)
                } else {
                    stackWithMessage
                }
                append(stack.replace("   ", "\t"))
            } else {
                append("${t::class.js.name}: ${t.message}")
            }
        }

        private fun withoutEndingNewLine(text: String?): String {
            if (text == null) return ""
            return if (text.endsWith("\n")) text.substringBeforeLast("\n") else text
        }

        private fun getStackWithMessage(t: Throwable, nullableStack: String): String {
            return when {
                nullableStack.startsWith("captureStack") -> {
                    val firstNewLine = nullableStack.indexOf('\n')
                    t::class.simpleName + ": " + withoutEndingNewLine(t.message) +
                        "\n   " + withoutEndingNewLine(nullableStack).substring(firstNewLine + 1).split('\n').joinToString(
                        "\n   "
                    )
                }
                nullableStack.isBlank() -> t.toString()
                else -> nullableStack
            }
        }

        internal fun DIV.convertNewLinesToBrTabToTwoSpacesAndParseUrls(message: String) {
            if (message.isEmpty()) return

            val messages = message.split("\n")
            convertTabToTwoSpacesAndUrlToLinks(messages[0])
            for (i in 1 until messages.size) {
                br
                convertTabToTwoSpacesAndUrlToLinks(messages[i])
            }
        }


        private val urlRegex = Regex("http(?:s)?://[^ ]+")

        private fun DIV.convertTabToTwoSpacesAndUrlToLinks(message: String) {
            var matchResult = urlRegex.find(message)
            if (matchResult != null) {
                var index = 0
                do {
                    val match = matchResult ?: throw ConcurrentModificationException(
                        "matchResult was suddenly null, this class is not thread-safe"
                    )
                    convertTabToTwoSpaces(message.substring(index, match.range.start))
                    val (url, nextIndex) = determineUrlAndNextIndex(match)
                    a(href = url) {
                        +url
                    }
                    index = nextIndex
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


        private fun determineUrlAndNextIndex(match: MatchResult): Pair<String, Int> {
            val tmpUrl = match.value
            return if (tmpUrl.endsWith(".")) {
                tmpUrl.substring(0, tmpUrl.length - 1) to match.range.endInclusive
            } else {
                tmpUrl to match.range.endInclusive + 1
            }
        }
    }
}
