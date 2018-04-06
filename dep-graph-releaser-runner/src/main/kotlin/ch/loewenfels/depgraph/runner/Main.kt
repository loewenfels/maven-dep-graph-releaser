package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.runner.Main.fileVerifier
import ch.loewenfels.depgraph.runner.commands.*
import ch.loewenfels.depgraph.runner.console.*
import java.io.File

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

fun String.toVerifiedFile(fileDescription: String): File = fileVerifier.file(this, fileDescription)
