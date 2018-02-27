package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("ch.loewenfels.depgraph.runner.MainKt")

fun main(vararg args: String) {
    if (args.isEmpty()) {
        error("""
            |No arguments supplied
            |
            |$allCommands
        """.trimMargin())
    }

    when (args[0]) {
        "json" -> json(args)
        "html" -> html(args)
        else -> error("Unknown command supplied\n$allCommands")
    }
}

private const val JSON_GROUP_ID = 1
private const val JSON_ARTIFACT_ID = 2
private const val JSON_DIR = 3
private const val JSON_JSON = 4

private fun json(args: Array<out String>) {
    if (args.size != 5) {
        error("""
            |Not enough or too many arguments supplied for command: json
            |
            |$jsonArguments
            |
            |${getGivenArgs(args)}
            |
            |Following an example:
            |./produce json com.example example-project ./repo ./release.json
        """.trimMargin())
    }

    val directoryToAnalyse = File(args[JSON_DIR])
    if (!directoryToAnalyse.exists()) {
        error("""
            |The given directory $directoryToAnalyse does not exist. Maybe you mixed up the order of the arguments?
            |
            |$jsonArguments
            |
            |${getGivenArgs(args)}
        """.trimMargin())
    }

    val json = File(args[JSON_JSON])
    if (!json.parentFile.exists()) {
        error("""The directory in which the resulting JSON file shall be created does not exists:
            |Directory: ${json.parentFile.canonicalPath}
        """.trimMargin())
    }

    if (json.exists()) {
        logger.info("The resulting JSON file already exists, going to overwrite it.")
    }

    val mavenProjectId = MavenProjectId(args[JSON_GROUP_ID], args[JSON_ARTIFACT_ID])
    Orchestrator.analyseAndCreateJson(directoryToAnalyse, json, mavenProjectId)
}

private const val HTML_JSON = 1
private const val HTML_HTML = 2

fun html(args: Array<out String>) {
    if (args.size != 3) {
        error("""
            |Not enough or too many arguments supplied for command: html
            |
            |$htmlArguments
            |
            |${getGivenArgs(args)}
            |
            |Following an example:
            |./produce html ./release.json ./pipeline.html
        """.trimMargin())
    }

    val json = File(args[HTML_JSON])
    if (!json.exists()) {
        error("""
            |The given JSON file $json does not exist. Maybe you mixed up the order of the arguments?
            |
            |$jsonArguments
            |
            |${getGivenArgs(args)}
        """.trimMargin())
    }

    val html = File(args[HTML_HTML])
    if (!html.parentFile.exists()) {
        error("""The directory in which the resulting HTML file shall be created does not exists:
            |Directory: ${html.parentFile.canonicalPath}
        """.trimMargin())
    }

    if (html.exists()) {
        logger.info("The resulting HTML file already exists, going to overwrite it.")
    }
    Orchestrator.createHtmlFromJson(json, html)
}

private fun getGivenArgs(args: Array<out String>) = "Given: ${args.joinToString(",")}"


private val jsonArguments = """
|json requires the following arguments in the given order:
|
|groupId    // maven groupId of the project which shall be released
|artifactId // maven artifactId of the project which shall be released
|dir        // path to the directory where all projects are
|json       // path + file name for the resulting json file
""".trimIndent()

private val htmlArguments = """
|html requires the following arguments in the given order:
|json       // path + file name for the input json file
|html       // path + file name for the resulting html file
""".trimMargin()

private val allCommands = """
|Currently we support the following commands:
|json       // analyse projects, create a release plan and serialize it to json
|html       // deserialize json and convert it to html
|
|$jsonArguments
|
|$htmlArguments
""".trimMargin()

private fun error(msg: String) = errorHandler.error(msg)

internal var errorHandler: ErrorHandler = object : ErrorHandler {
    override fun error(msg: String) {
        System.err.println(msg)
        System.exit(-1)
    }
}

internal interface ErrorHandler {
    fun error(msg: String)
}


