package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.runner.commands.*
import ch.loewenfels.depgraph.runner.console.*

object Main {
    @JvmStatic
    fun main(vararg args: String?) {
        val commands = listOf(
            Json,
            PrintReleasableProjects,
            Html,
            UpdateDependency,
            JenkinsRemoteM2Release,
            JenkinsPipeline
        )
        dispatch(args, errorHandler, commands)
    }

    internal var errorHandler: ErrorHandler = SystemExitErrorHandler
    internal var fileVerifier: FileVerifier = OnlyFolderAndSubFolderFileVerifier
}
