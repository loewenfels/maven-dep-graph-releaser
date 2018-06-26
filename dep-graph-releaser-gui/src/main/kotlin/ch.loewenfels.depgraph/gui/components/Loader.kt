package ch.loewenfels.depgraph.gui.components

import ch.loewenfels.depgraph.gui.elementById
import kotlinx.html.DIV
import kotlinx.html.br
import kotlinx.html.code
import kotlinx.html.dom.append
import kotlinx.html.js.div
import kotlinx.html.js.i
import kotlinx.html.js.p
import kotlinx.html.js.span
import kotlinx.html.p
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import kotlin.dom.removeClass

object Loader {

    init{
        val loader = elementById("loader")
        loader.childNodes.asList().forEach {
            if (it.nodeType == 3.toShort()) {
                loader.removeChild(it)
            }
        }
    }

    fun updateLoaderToLoadApiToken() {
        updateLoader("Retrieving API Token") {
            p {
                +"If you keep seeing this after a few seconds, then either an error occurred (see bottom of the page) and if not then most probably CORS was not successful and a request was blocked by the server."
                br
                +"You can verify it by opening the developer console(F12 in many browsers)"
            }
            p {
                +"In case you only want to see the resulting pipeline without release functionality, then please remove "
                code { +"&publishUrl = ..." }
                +"from the current URL."
            }
        }
    }

    fun updateToLoadingJson() {
        updateLoader("Loading Json") {
            getDefaultLoadingMessage()
        }
    }


    fun updateToLoadOtherTokens() {
        updateLoader("Loading other API Tokens (from remote Jenkins servers)") {
            getDefaultLoadingMessage()
        }
    }

    fun updateToRecoverOngoingProcess() {
        updateLoader("Recovering ongoing process") {
            p { +"Should disappear after half a minute or so; otherwise most likely an error occurred (see bottom of the page)." }
        }
    }

    fun updateToLoadPipeline() {
        updateLoader("Loading Pipeline") {
            getDefaultLoadingMessage()
        }
    }

    private fun DIV.getDefaultLoadingMessage() {
        p {
            +"Should disappear after a few seconds; otherwise either an error occurred (see bottom of the page) and if not then most likely the request silently failed."
            br
            +"You can verify it by opening the developer console (F12 in many browsers)"
        }
    }

    private fun updateLoader(newItem: String, divContent: DIV.() -> Unit) {
        val loader = elementById("loader")
        loader.removeChild(loader.lastChild!!)
        val lastItem = loader.lastChild!!
        val icon = lastItem.firstChild as HTMLElement
        icon.removeClass("waiting")
        icon.innerText = "check_box"
        val text = lastItem.lastChild as HTMLElement
        text.innerText = text.innerText.substringBefore("...") + " successful"
        loader.append {
            p {
                i("material-icons waiting") { +"check_box_outline_blank" }
                span { +newItem; +"..." }
            }
            div { divContent() }
        }
    }
}
