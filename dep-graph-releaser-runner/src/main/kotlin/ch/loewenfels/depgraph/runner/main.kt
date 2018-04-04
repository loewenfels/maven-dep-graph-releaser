package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.runner.Html.htmlArguments
import ch.loewenfels.depgraph.runner.Json.jsonArguments
import ch.loewenfels.depgraph.runner.UpdateDependency.updateArguments
import java.io.File

fun main(vararg args: String) {
    if (args.isEmpty()) {
        error("""
            |No arguments supplied
            |
            |$allCommands
        """.trimMargin())
    }

    when (args[0]) {
        "json" -> Json(args)
        "html" -> Html(args)
        "update" -> UpdateDependency(args)
        else -> error("Unknown command supplied\n$allCommands")
    }
}

private val allCommands = """
|Currently we support the following commands:
|json       // analyse projects, create a release plan and serialize it to json
|html       // deserialize json and convert it to html
|
|$jsonArguments
|
|$htmlArguments
|
|$updateArguments
""".trimMargin()

internal fun getGivenArgs(args: Array<out String>) = "Given: ${args.joinToString(" ")}"

internal fun error(msg: String) = errorHandler.error(msg)

internal var errorHandler: ErrorHandler = object : ErrorHandler {
    override fun error(msg: String) {
        System.err.println(msg)
        System.exit(-1)
    }
}
internal var fileVerifier: FileVerifier = object : FileVerifier{
    override fun file(path: String,  fileDescription: String): File {
        require(!path.contains("..")) {
            "Using `..` in the path of the $fileDescription is prohibited due to security reasons."
        }
        val secureFile = File(path)
        require(secureFile.absolutePath.startsWith(File("").absolutePath)) {
            "$fileDescription was neither a relative path nor " +
                "an absolute path which has the same parent folder as where this command was executed"
        }
        return secureFile
    }
}

internal interface ErrorHandler {
    fun error(msg: String)
}
internal interface FileVerifier {
    fun file(path: String, fileDescription: String): File
}


