package ch.loewenfels.depgraph.gui

import org.w3c.dom.HTMLElement
import kotlin.browser.document

class Downloader {
    fun download(json: String) {
        val a = document.createElement("a") as HTMLElement
        a.setAttribute("href", "data:text/plain;charset=utf-8,${encodeURIComponent(json)}")
        a.setAttribute("download", "release.json")
        a.style.display = "none"
        document.body!!.appendChild(a)
        a.click()
        document.body!!.removeChild(a)
    }
}
