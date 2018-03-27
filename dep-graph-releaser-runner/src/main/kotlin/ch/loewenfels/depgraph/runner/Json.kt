package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.maven.Analyser
import java.io.File

object Json {

    private const val JSON_GROUP_ID = 1
    private const val JSON_ARTIFACT_ID = 2
    private const val JSON_DIR = 3
    private const val JSON_JSON = 4
    private const val JSON_MISSING_PARENT_ANALYSIS = 5
    const val MPOFF = "-mpoff"
    val jsonArguments = """
        |json requires the following arguments in the given order:
        |
        |groupId    // maven groupId of the project which shall be released
        |artifactId // maven artifactId of the project which shall be released
        |dir        // path to the directory where all projects are
        |json       // path + file name for the resulting json file
        |($MPOFF)   // optionally: turns missing parent analysis off
        """.trimMargin()


    operator fun invoke(args: Array<out String>) {
        if (args.size < 5 || args.size > 6) {
            ch.loewenfels.depgraph.runner.error(
                """
            |Not enough or too many arguments supplied for command: json
            |
            |$jsonArguments
            |
            |${getGivenArgs(args)}
            |
            |Following an example:
            |./produce json com.example example-project ./repo ./release.json
        """.trimMargin()
            )
        }

        val turnMissingPartnerAnalysisOff = args.size == 6
        if (turnMissingPartnerAnalysisOff && args[JSON_MISSING_PARENT_ANALYSIS].toLowerCase() != MPOFF){
            ch.loewenfels.depgraph.runner.error(
                """
            |Last argument supplied can only be $MPOFF for command: json
            |
            |$jsonArguments
            |
            |${getGivenArgs(args)}
            |
            |Following an example:
            |./produce json com.example example-project ./repo ./release.json -mpoff
        """.trimMargin()
            )
        }

        val directoryToAnalyse = fileVerifier.file(args[JSON_DIR], "directory to analyse")
        if (!directoryToAnalyse.exists()) {
            ch.loewenfels.depgraph.runner.error(
                """
            |The given directory $directoryToAnalyse does not exist. Maybe you mixed up the order of the arguments?
            |
            |$jsonArguments
            |
            |${getGivenArgs(args)}
        """.trimMargin()
            )
        }

        val json = fileVerifier.file(args[JSON_JSON], "json file")
        if (!json.parentFile.exists()) {
            ch.loewenfels.depgraph.runner.error(
                """The directory in which the resulting JSON file shall be created does not exists:
            |Directory: ${json.parentFile.absolutePath}
        """.trimMargin()
            )
        }
        val mavenProjectId = MavenProjectId(args[JSON_GROUP_ID], args[JSON_ARTIFACT_ID])
        val options = Analyser.Options(!turnMissingPartnerAnalysisOff)
        Orchestrator.analyseAndCreateJson(directoryToAnalyse, json, mavenProjectId, options)
    }
}
