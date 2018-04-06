package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.console.expectedArgsAndGiven
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.maven.Analyser
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator
import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.toOptionalArgs
import ch.loewenfels.depgraph.runner.toVerifiedFile

object Json : ConsoleCommand {

    internal const val MAVEN_PARENT_ANALYSIS_OFF = "-mpoff"
    private const val DISABLE_RELEASE_FOR = "-dr="

    override val name = "json"
    override val description = "analyse projects, create a release plan and serialize it to json"
    override val example = "./produce $name com.example example-project ./repo ./release.json"
    override val arguments by lazy {
        """
        |$name requires the following arguments in the given order:
        |groupId     // maven groupId of the project which shall be released
        |artifactId  // maven artifactId of the project which shall be released
        |dir         // path to the directory where all projects are
        |json        // path + file name for the resulting json file
        |(${DISABLE_RELEASE_FOR}Regex) // optionally: regex specifying for which projects
        |               the release commands have to be disabled
        |($MAVEN_PARENT_ANALYSIS_OFF)    // optionally: turns missing parent analysis off
        """.trimMargin()
    }
    override fun numOfArgsNotOk(number: Int) = number < 5 || number > 7

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, groupId, artifactId, unsafeDirectoryToAnalyse, jsonFile) = args
        val (disableReleaseFor, missingParentAnalysis ) = args.drop(5).toOptionalArgs(2)

        val disableReleaseForRegex = if (disableReleaseFor != null) {
            if (!disableReleaseFor.startsWith(DISABLE_RELEASE_FOR) && disableReleaseFor.toLowerCase() != MAVEN_PARENT_ANALYSIS_OFF) {
                errorHandler.error(
                    """
                    |Last argument supplied can only be ${DISABLE_RELEASE_FOR}Regex or $MAVEN_PARENT_ANALYSIS_OFF for command: $name
                    |
                    |${expectedArgsAndGiven(this, args)}
                    |
                    |Following an example:
                    |./produce json com.example example-project ./repo ./release.json -dr=ch.loewenfels:dist.*
                    """.trimMargin()
                )
            }
            Regex(disableReleaseFor.substringAfter(DISABLE_RELEASE_FOR))
        } else {
            Regex("^$") //does only match the empty string
        }


        val turnMissingPartnerAnalysisOff = if (disableReleaseFor != null) {
            if (missingParentAnalysis != null) {
                if (missingParentAnalysis.toLowerCase() != MAVEN_PARENT_ANALYSIS_OFF) {
                    errorHandler.error(
                        """
                        |Last argument supplied can only be $MAVEN_PARENT_ANALYSIS_OFF for command: $name
                        |
                        |${expectedArgsAndGiven(this, args)}
                        |
                        |Following an example:
                        |./produce json com.example example-project ./repo ./release.json -mpoff
                        """.trimMargin()
                    )
                }
                true
            } else {
                disableReleaseFor.toLowerCase() == MAVEN_PARENT_ANALYSIS_OFF
            }
        } else {
            false
        }

        val directoryToAnalyse = unsafeDirectoryToAnalyse.toVerifiedFile("directory to analyse")
        if (!directoryToAnalyse.exists()) {
            errorHandler.error(
                """
                |The given directory does not exist. Maybe you mixed up the order of the arguments?
                |directory: ${directoryToAnalyse.absolutePath}
                |
                |${expectedArgsAndGiven(this, args)}
                """.trimMargin()
            )
        }

        val json = jsonFile.toVerifiedFile("json file")
        if (!json.parentFile.exists()) {
            errorHandler.error(
                """
                |The directory in which the resulting JSON file shall be created does not exist.
                |Directory: ${json.parentFile.absolutePath}
                """.trimMargin()
            )
        }

        val mavenProjectId = MavenProjectId(groupId, artifactId)
        val analyserOptions = Analyser.Options(!turnMissingPartnerAnalysisOff)
        val releasePlanCreatorOptions = JenkinsReleasePlanCreator.Options(disableReleaseForRegex)

        Orchestrator.analyseAndCreateJson(
            directoryToAnalyse,
            json,
            mavenProjectId,
            analyserOptions,
            releasePlanCreatorOptions
        )
    }
}
