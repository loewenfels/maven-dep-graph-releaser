package ch.loewenfels.depgraph.gui

import kotlinx.html.HTMLTag
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.dom.addClass
import kotlin.dom.hasClass
import kotlin.dom.removeClass

/**
 * Hack to get the underlying [HTMLElement] of the the given [HTMLTag].
 */
fun HTMLTag.getUnderlyingHtmlElement(): HTMLElement {
    val arr = this.consumer.asDynamic().downstream.path_0.toArray() as Array<HTMLElement>
    return arr[arr.size - 1]
}

fun HTMLElement.addClickEventListener(action: (Event) -> Unit) {
    this.addEventListener("click", { withErrorHandling(it, action) })
}

fun HTMLElement.addChangeEventListener(action: (Event) -> Unit) {
    this.addEventListener("change", { withErrorHandling(it, action) })
}

fun HTMLElement.toggleClass(cssClass: String) {
    if (hasClass(cssClass)) {
        removeClass(cssClass)
    } else {
        addClass(cssClass)
    }
}

fun withErrorHandling(event: Event, action: (Event) -> Unit) {
    try {
        action(event)
    } catch (t: Throwable) {
        showError(
            RuntimeException("An unexpected error occurred. Please report a bug with the following information.", t)
        )
    }
}
