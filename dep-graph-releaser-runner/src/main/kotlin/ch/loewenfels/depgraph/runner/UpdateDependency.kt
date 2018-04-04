package ch.loewenfels.depgraph.runner

object UpdateDependency {

    private const val ARG_POM_FILE = 1
    private const val ARG_GROUP_ID = 2
    private const val ARG_ARTIFACT_ID = 3
    private const val ARG_NEW_VERSION = 4
    val updateArguments = """
        |update requires the following arguments in the given order:
        |pom         // path to the pom file which shall be updated
        |groupId     // maven groupId of the dependency which shall be updated
        |artifactId  // maven artifactId of the dependency which shall be updated
        |newVersion  // the new version which shall be used for the dependency
        """.trimMargin()

    operator fun invoke(args: Array<out String>) {
        if (args.size != 5) {
            error(
                """
                |Not enough or too many arguments supplied for command: update
                |
                |$updateArguments
                |
                |${getGivenArgs(args)}
                |
                |Following an example:
                |./update ./pom.xml com.example example-project 2.0.0
                """.trimMargin()
            )
        }

        val pom = fileVerifier.file(args[ARG_POM_FILE], "pom file")
        if (!pom.exists()) {
            error(
                """
                |The given pom file does not exist. Maybe you mixed up the order of the arguments?
                |pom: ${pom.absolutePath}
                |
                |${Json.jsonArguments}
                |
                |${getGivenArgs(args)}
                """.trimMargin()
            )
        }
        Orchestrator.updateDependency(pom, args[ARG_GROUP_ID], args[ARG_ARTIFACT_ID], args[ARG_NEW_VERSION])
    }
}
