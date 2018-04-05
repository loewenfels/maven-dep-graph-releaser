package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.runner.commands.Html
import ch.loewenfels.depgraph.runner.commands.JenkinsRemoteM2Release
import ch.loewenfels.depgraph.runner.commands.Json
import ch.loewenfels.depgraph.runner.commands.UpdateDependency
import ch.loewenfels.depgraph.runner.console.*

object Main {
    @JvmStatic
    fun main(vararg args: String?) {
        val commands = listOf(
            Json,
            Html,
            UpdateDependency,
            JenkinsRemoteM2Release
        )
        dispatch(args, errorHandler, commands)
    }

    internal var errorHandler: ErrorHandler = SystemExitErrorHandler
    internal var fileVerifier: FileVerifier = OnlyFolderAndSubFolderFileVerifier
}


