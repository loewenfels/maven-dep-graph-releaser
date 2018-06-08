package ch.loewenfels.depgraph.runner.console

val errorHandler = object : ErrorHandler {
    override fun error(msg: String) = throw IllegalStateException(msg)
}
