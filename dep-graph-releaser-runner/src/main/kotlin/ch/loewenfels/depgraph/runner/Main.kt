package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.runner.commands.Html
import ch.loewenfels.depgraph.runner.commands.Json
import ch.loewenfels.depgraph.runner.commands.UpdateDependency
import ch.loewenfels.depgraph.runner.console.*

object Main {
    @JvmStatic
    fun main(vararg args: String?) {
        dispatch(args, errorHandler, listOf(Json, Html, UpdateDependency))
    }

    internal var errorHandler: ErrorHandler = SystemExitErrorHandler
    internal var fileVerifier: FileVerifier = OnlyFolderAndSubFolderFileVerifier
}


