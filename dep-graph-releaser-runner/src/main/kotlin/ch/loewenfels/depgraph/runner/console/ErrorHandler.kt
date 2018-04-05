package ch.loewenfels.depgraph.runner.console

interface ErrorHandler {
    fun error(msg: String): Nothing
}
