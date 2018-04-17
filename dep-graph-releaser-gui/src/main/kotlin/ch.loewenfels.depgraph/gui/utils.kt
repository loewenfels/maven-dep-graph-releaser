package ch.loewenfels.depgraph.gui

import kotlin.browser.document
import kotlin.browser.window
import kotlin.js.Promise

fun generateUniqueId(): String =
    js("Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);") as String

fun <T> sleep(ms: Int, action: () -> T): Promise<T> {
    val p: Promise<Any> = Promise({ resolve, _ -> window.setTimeout(resolve, ms) })
    return p.then { action() }
}

fun changeCursorToProgress() {
    document.body!!.style.cursor = "progress"
}

fun changeCursorBackToNormal() {
    document.body!!.style.cursor = "default"
}

fun <T, S> Promise<T>.finally(action: (T?) -> S): Promise<S> {
    return this.then { action(it) }
        .catch { action(null) }.asDynamic() as Promise<S>
}
