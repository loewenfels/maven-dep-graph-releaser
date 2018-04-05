package ch.loewenfels.depgraph.runner.console

object SystemExitErrorHandler : ErrorHandler {
    override fun error(msg: String): Nothing {
        System.err.println(msg)
        System.exit(-1)
        throw RuntimeException("System.exit(-1) did not abort execution")
    }
}
