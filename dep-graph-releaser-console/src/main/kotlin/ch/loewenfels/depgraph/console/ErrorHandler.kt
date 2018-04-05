package ch.loewenfels.depgraph.console

interface ErrorHandler {
    fun error(msg: String): Nothing
}
