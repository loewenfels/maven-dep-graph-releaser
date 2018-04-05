package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.runner.console.ConsoleCommand
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.expectedArgsAndGiven
import ch.loewenfels.depgraph.runner.Main.fileVerifier

object UpdateDependency : ConsoleCommand {

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
        val (_, pomFile, groupId, artifactId, newVersion) = args
        val pom = fileVerifier.file(pomFile, "pom file")
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
        Orchestrator.updateDependency(pom, groupId, artifactId, newVersion)
    }
}
