package ch.loewenfels.depgraph.gui

import kotlin.browser.document
import kotlin.browser.window
import kotlin.js.Promise

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
    return this
        .then { action(it) }
        .catch { t -> action(null); throw t }
}
