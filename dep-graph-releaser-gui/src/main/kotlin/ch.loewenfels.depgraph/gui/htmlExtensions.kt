package ch.loewenfels.depgraph.gui

import kotlinx.html.HTMLTag
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.dom.addClass
import kotlin.dom.hasClass
import kotlin.dom.removeClass
import kotlin.js.Promise

/**
 * Hack to get the underlying [HTMLElement] of the the given [HTMLTag].
 */
fun HTMLTag.getUnderlyingHtmlElement(): HTMLElement {
    var d = this.consumer.asDynamic()
    if (d.downstream != null) {
        d = d.downstream
    }
    val arr = d.path_0.toArray() as Array<HTMLElement>
    return arr[arr.size - 1]
}

fun HTMLElement.addClickEventListener(action: (Event) -> Any) {
    this.addEventListener("click", { withErrorHandling(it, action) })
}

fun HTMLElement.addChangeEventListener(action: (Event) -> Any) {
    this.addEventListener("change", { withErrorHandling(it, action) })
}

fun HTMLElement.toggleClass(cssClass: String) {
    if (hasClass(cssClass)) {
        removeClass(cssClass)
    } else {
        addClass(cssClass)
    }
}

fun withErrorHandling(event: Event, action: (Event) -> Any) {
    Promise.resolve(1).then {
        action(event)
    }.catch { t ->
        showThrowableAndThrow(
            Error("An unexpected error occurred. Please report a bug with the following information.", t)
        )
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
