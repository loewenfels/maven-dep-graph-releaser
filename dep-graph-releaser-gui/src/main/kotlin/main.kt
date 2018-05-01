import ch.loewenfels.depgraph.gui.App
import kotlin.browser.window

@JsName("main")
fun main() {
    window.onload = { App() }
}

@Suppress("unused")
val onlyUsedToCallMain = main()

@JsName("failAfter")
var failAfter = 1000
