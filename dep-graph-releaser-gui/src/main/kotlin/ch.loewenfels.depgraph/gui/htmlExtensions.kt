package ch.loewenfels.depgraph.gui

import kotlinx.html.HTMLTag
import org.w3c.dom.HTMLElement

/**
 * Hack to get the underlying [HTMLElement] of the the given [HTMLTag].
 */
fun HTMLTag.getUnderlyingHtmlElement(): HTMLElement {
    val arr = this.consumer.asDynamic().downstream.path_0.toArray() as Array<HTMLElement>
    return arr[arr.size - 1]
}

fun HTMLElement.addClickEventListener(action: () -> Unit) {
    this.addEventListener("click", { withErrorHandling { action() } })
}

fun withErrorHandling(action: () -> Unit) {
    try {
        action()
    } catch (t: Throwable) {
        showError(
            RuntimeException("An unexpected error occurred. Please report a bug with the following information.", t)
        )
    }
}
