package ch.loewenfels.depgraph.gui.jobexecution

@Suppress("DataClassPrivateConstructor")
data class UrlWithSlashAtTheEnd private constructor(val url: String) {

    operator fun plus(s: String) = url + s
    override fun toString(): String = url

    companion object {
        fun create(url: String) = UrlWithSlashAtTheEnd(if (url.endsWith("/")) url else "$url/")
    }
}
