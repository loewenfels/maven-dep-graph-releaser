package ch.loewenfels.depgraph.gui.actions

import ch.loewenfels.depgraph.gui.serialization.ModifiableState
import ch.loewenfels.depgraph.gui.components.encodeURIComponent
import org.w3c.dom.HTMLElement
import kotlin.browser.document

class Downloader(private val modifiableState: ModifiableState) {

    fun download() {
        val json = modifiableState.getJsonWithAppliedChanges()
        download("release.json", json)
    }

    companion object {
        fun download(fileName: String, content: String){
            val a = document.createElement("a") as HTMLElement
            a.setAttribute("href", "data:text/plain;charset=utf-8,${encodeURIComponent(content)}")
            a.setAttribute("download", fileName)
            a.style.display = "none"
            document.body!!.appendChild(a)
            a.click()
            document.body!!.removeChild(a)
        }
    }
}
