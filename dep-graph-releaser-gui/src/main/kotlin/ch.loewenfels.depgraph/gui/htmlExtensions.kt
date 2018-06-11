package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.gui.jobexecution.GITHUB_NEW_ISSUE
import kotlinx.html.HTMLTag
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.dom.addClass
import kotlin.dom.hasClass
import kotlin.dom.removeClass
import kotlin.js.Promise

/**
 * Hack to get the underlying [HTMLElement] of the the given [HTMLTag]. Fails if
 * a) downstream is renamed (is a private field)
 * b) variable mangling process changes in kotlin, then it might use another name than path_0
 */
fun HTMLTag.getUnderlyingHtmlElement(): HTMLElement {
    var d = this.consumer.asDynamic()
    if (d.downstream != null) {
        d = d.downstream
    }
    val arr = d.path_0.toArray() as Array<HTMLElement>
    return arr[arr.size - 1]
}

fun HTMLElement.addClickEventListener(options: dynamic = js("({})"), action: (Event) -> Any) {
    this.addEventListener("click", { withErrorHandling(it, action) }, options)
}

fun HTMLElement.addChangeEventListener(options: dynamic = js("({})"), action: (Event) -> Any) {
    this.addEventListener("change", { withErrorHandling(it, action) }, options)
}

fun HTMLElement.toggleClass(cssClass: String) {
    if (hasClass(cssClass)) {
        removeClass(cssClass)
    } else {
        addClass(cssClass)
    }
}

private fun withErrorHandling(event: Event, action: (Event) -> Any) {
    Promise.resolve(1).then {
        action(event)
    }.catch { t ->
        val message = "An unexpected error occurred." +
            "\nPlease report a bug with the following information at $GITHUB_NEW_ISSUE"
        showThrowableAndThrow(Error(message, t))
    }
}

fun HTMLElement.getOldTitle() = this.asDynamic().oldTitle as String
fun HTMLElement.getOldTitleOrNull() = this.asDynamic().oldTitle as? String
fun HTMLElement.setTitleSaveOld(newTitle: String) {
    if (this.title != newTitle) {
        this.asDynamic().oldTitle = this.title
    }
    this.title = newTitle
}
