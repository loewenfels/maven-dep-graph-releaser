package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import java.io.File

fun main(vararg args: String) {
    if (args.size != 5) {
        error("""
            |Not enough arguments supplied!
            |
            |$argumentOrder
            |
            |Given: ${args.joinToString(",")}
            |
            |Following an example (assuming the required jars are in folder lib and projects in repo):
            |./produce json com.example example-project 1.0.0 ./repo ./release.json
        """.trimMargin())
    }

    if (args[0] != "json") {
        error("\n$argumentOrder")
    }

    val directoryToAnalyse = File(args[DIR])
    if (!directoryToAnalyse.exists()) {
        error("""
            |The given directory $directoryToAnalyse does not exist. Maybe you mixed up the order?
            |
            |$argumentOrder
        """.trimMargin())
    }

    val json = File(args[JSON])
    if (!json.parentFile.exists()) {
        error("""The directory in which the resulting JSON file shall be created does not exists:
            |Directory: ${json.parentFile.canonicalPath}
        """.trimMargin())
    }

    if (json.exists()) {
        println("The resulting JSON file already exists, going to overwrite it.")
    }

    val mavenProjectId = MavenProjectId(args[GROUP_ID], args[ARTIFACT_ID])
    println("Going to analyse: ${directoryToAnalyse.canonicalPath}")

    Orchestrator.analyseAndCreateJson(directoryToAnalyse, json, mavenProjectId)
    println("Created file: ${json.canonicalPath}")
}

private const val GROUP_ID = 1
private const val ARTIFACT_ID = 2
private const val DIR = 3
private const val JSON = 4

private val argumentOrder = """
|Currently we support only the command: json
|It requires the following arguments in the given order:
|groupId (maven groupId of the project which shall be released)
|artifactId (maven artifactId of the project which shall be released
|dir (path to the directory where all projects are)
|json (path incl. file name for the resulting json file)
""".trimMargin()


private fun error(msg: String) = errorHandler.error(msg)

internal var errorHandler: ErrorHandler = object: ErrorHandler {
    override fun error(msg: String) {
        System.err.println(msg)
        System.exit(-1)
    }
}

internal interface ErrorHandler {
    fun error(msg: String)
}


