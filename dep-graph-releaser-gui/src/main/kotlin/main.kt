import ch.loewenfels.depgraph.gui.App
import kotlin.browser.window

@JsName("main")
fun main() {
    window.onload = { App() }
}

@Suppress("unused")
val onlyUsedToCallMain = main()

@JsName("failAfterSteps")
var failAfterSteps = 10000
@JsName("waitBetweenSteps")
var waitBetweenSteps = 500
@JsName("stepWise")
var stepWise = false

@JsName("options")
fun options() {
    console.log(
        mapOf(
            "failAfterSteps" to failAfterSteps,
            "waitBetweenSteps" to waitBetweenSteps,
            "stepWise" to stepWise
        ).entries.joinToString("\n") { (k, v) -> "$k: $v" }
    )
}
