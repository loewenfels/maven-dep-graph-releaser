package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.Main
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.expectedArgsAndGiven
import java.io.File

fun String.toVerifiedFile(fileDescription: String): File = Main.fileVerifier.file(this, fileDescription)

fun String.toVerifiedExistingFile(
    fileDescription: String,
    command: ConsoleCommand,
    args: Array<out String>,
    errorHandler: ErrorHandler,
    suffix: String = ""
): File {
    val safeFile = toVerifiedFile(fileDescription)
    if (!safeFile.exists()) {
        errorHandler.error(
            """
            |The given $fileDescription$suffix does not exist. Maybe you mixed up the order of the arguments?"
            |$fileDescription: ${safeFile.absolutePath}
            |
            |${expectedArgsAndGiven(command, args)}
            """.trimMargin()
        )
    }
    return safeFile
}

fun String.toVerifiedFileIfParentExists(
    fileDescription: String,
    errorHandler: ErrorHandler
): File {
    val file = toVerifiedFile(fileDescription)
    if (!file.parentFile.exists()) {
        errorHandler.error(
            """
                |The directory in which the resulting $fileDescription shall be created does not exist.
                |Directory: ${file.parentFile.absolutePath}
                """.trimMargin()
        )
    }
    return file
}
