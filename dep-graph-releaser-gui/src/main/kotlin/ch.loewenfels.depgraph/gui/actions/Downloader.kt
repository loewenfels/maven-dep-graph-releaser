package ch.loewenfels.depgraph.gui.actions

import ch.loewenfels.depgraph.gui.ModifiableJson
import ch.loewenfels.depgraph.gui.encodeURIComponent
import org.w3c.dom.HTMLElement
import kotlin.browser.document

class Downloader(private val modifiableJson: ModifiableJson) {

    fun download() {
        val json = modifiableJson.getJsonWithAppliedChanges()
        val a = document.createElement("a") as HTMLElement
        a.setAttribute("href", "data:text/plain;charset=utf-8,${encodeURIComponent(json)}")
        a.setAttribute("download", "release.json")
        a.style.display = "none"
        document.body!!.appendChild(a)
        a.click()
        document.body!!.removeChild(a)
    }
}
