package ch.loewenfels.depgraph.gui

import kotlin.browser.window
import kotlin.dom.hasClass
import kotlin.dom.removeClass

class Menu {

    private val save get() = elementById("save")
    private val dryRun get() = elementById("dry")
    private val build get() = elementById("build")

    init {
        window.onbeforeunload = {
            if (!save.hasClass(DEACTIVATED)) {
                "Your changes will be lost, sure you want to leave the page?"
            } else {
                null
            }
        }
        save.addEventListener("click", {
            //TODO implement
        })
        dryRun.addEventListener("click", {
            //TODO implement
        })
        build.addEventListener("click", {
            //TODO implement
        })
    }

    fun activateSaveButton() {
        save.removeClass(DEACTIVATED)
    }

    companion object {
        private const val DEACTIVATED = "deactivated"
    }
}
