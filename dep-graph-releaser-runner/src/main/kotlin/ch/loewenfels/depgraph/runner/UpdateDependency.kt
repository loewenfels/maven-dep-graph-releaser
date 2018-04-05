package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.console.ConsoleCommand
import ch.loewenfels.depgraph.console.ErrorHandler
import ch.loewenfels.depgraph.console.expectedArgsAndGiven
import ch.loewenfels.depgraph.runner.Main.fileVerifier

object UpdateDependency: ConsoleCommand {
    private const val ARG_POM_FILE = 1
    private const val ARG_GROUP_ID = 2
    private const val ARG_ARTIFACT_ID = 3
    private const val ARG_NEW_VERSION = 4

    override val name = "update"
    override val description = "updates the given dependency to the given version"
    override val example = "./update ./pom.xml com.example example-project 1.2.0"
    override val arguments = """
        |update requires the following arguments in the given order:
        |pom         // path to the pom file which shall be updated
        |groupId     // maven groupId of the dependency which shall be updated
        |artifactId  // maven artifactId of the dependency which shall be updated
        |newVersion  // the new version which shall be used for the dependency
        """.trimMargin()
    override fun numOfArgsNotOk(number: Int) = number != 5

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val pom = fileVerifier.file(args[ARG_POM_FILE], "pom file")
        if (!pom.exists()) {
            errorHandler.error(
                """
                |The given pom file does not exist. Maybe you mixed up the order of the arguments?
                |pom: ${pom.absolutePath}
                |
                |${expectedArgsAndGiven(this, args)}
                """.trimMargin()
            )
        }
        Orchestrator.updateDependency(pom, args[ARG_GROUP_ID], args[ARG_ARTIFACT_ID], args[ARG_NEW_VERSION])
    }
}
