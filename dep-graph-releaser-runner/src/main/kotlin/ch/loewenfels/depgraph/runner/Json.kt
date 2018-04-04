package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.maven.Analyser
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator

object Json {

    private const val ARG_GROUP_ID = 1
    private const val ARG_ARTIFACT_ID = 2
    private const val ARG_DIR = 3
    private const val ARG_JSON = 4
    private const val ARG_DISABLE_RELEASE_FOR = 5
    private const val ARG_MISSING_PARENT_ANALYSIS = 6

    internal const val MAVEN_PARENT_ANALYSIS_OFF = "-mpoff"
    private const val DISABLE_RELEASE_FOR = "-dr="
    val jsonArguments = """
        |json requires the following arguments in the given order:
        |groupId     // maven groupId of the project which shall be released
        |artifactId  // maven artifactId of the project which shall be released
        |dir         // path to the directory where all projects are
        |json        // path + file name for the resulting json file
        |(${DISABLE_RELEASE_FOR}Regex) // optionally: regex specifying for which projects
        |               the release commands have to be disabled
        |($MAVEN_PARENT_ANALYSIS_OFF)    // optionally: turns missing parent analysis off
        """.trimMargin()


    operator fun invoke(args: Array<out String>) {
        if (args.size < 5 || args.size > 7) {
            error(
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

        val disableReleaseFor = if (args.size >= 6) {
            val dr = args[ARG_DISABLE_RELEASE_FOR]
            if (!dr.startsWith(DISABLE_RELEASE_FOR) && dr.toLowerCase() != MAVEN_PARENT_ANALYSIS_OFF) {
                error(
                    """
                    |Last argument supplied can only be $DISABLE_RELEASE_FOR=Regex or $MAVEN_PARENT_ANALYSIS_OFF for command: json
                    |
                    |$jsonArguments
                    |
                    |${getGivenArgs(args)}
                    |
                    |Following an example:
                    |./produce json com.example example-project ./repo ./release.json -dr=ch.loewenfels:dist.*
                    """.trimMargin()
                )
            }
            Regex(dr.substringAfter(DISABLE_RELEASE_FOR))
        } else {
            Regex("^$") //does only match the empty string
        }


        val turnMissingPartnerAnalysisOff = if (args.size >= 6) {
            if (args.size == 7) {
                if(args[ARG_MISSING_PARENT_ANALYSIS].toLowerCase() != MAVEN_PARENT_ANALYSIS_OFF) {
                    error(
                        """
                        |Last argument supplied can only be $MAVEN_PARENT_ANALYSIS_OFF for command: json
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
                true
            } else {
                args[ARG_DISABLE_RELEASE_FOR].toLowerCase() == MAVEN_PARENT_ANALYSIS_OFF
            }
        } else {
            false
        }

        val directoryToAnalyse = fileVerifier.file(args[ARG_DIR], "directory to analyse")
        if (!directoryToAnalyse.exists()) {
            error("""
                |The given directory does not exist. Maybe you mixed up the order of the arguments?
                |directory: ${directoryToAnalyse.absolutePath}
                |
                |$jsonArguments
                |
                |${getGivenArgs(args)}
                """.trimMargin()
            )
        }

        val json = fileVerifier.file(args[ARG_JSON], "json file")
        if (!json.parentFile.exists()) {
            error(
                """
                |The directory in which the resulting JSON file shall be created does not exist.
                |Directory: ${json.parentFile.absolutePath}
                """.trimMargin()
            )
        }

        val mavenProjectId = MavenProjectId(args[ARG_GROUP_ID], args[ARG_ARTIFACT_ID])
        val analyserOptions = Analyser.Options(!turnMissingPartnerAnalysisOff)
        val releasePlanCreatorOptions = JenkinsReleasePlanCreator.Options(disableReleaseFor)

        Orchestrator.analyseAndCreateJson(
            directoryToAnalyse,
            json,
            mavenProjectId,
            analyserOptions,
            releasePlanCreatorOptions
        )
    }
}
