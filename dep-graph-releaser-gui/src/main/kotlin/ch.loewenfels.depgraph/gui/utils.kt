package ch.loewenfels.depgraph.gui

import kotlin.browser.window
import kotlin.js.Promise

fun generateUniqueId(): String =
    js("Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);") as String

fun <T> sleep(ms: Int, action: () -> T): Promise<T> {
    val p: Promise<Any> = Promise({ resolve, _ -> window.setTimeout(resolve, ms) })
    return p.then { action() }
}
