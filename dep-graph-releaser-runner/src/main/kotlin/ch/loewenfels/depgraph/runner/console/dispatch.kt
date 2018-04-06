package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.ConsoleCommand
import ch.tutteli.kbox.appendToStringBuilder

fun dispatch(
    args: Array<out String?>,
    errorHandler: ErrorHandler,
    commandsList: List<ConsoleCommand>
) {
    if (commandsList.isEmpty()) {
        errorHandler.error("Main is misconfigured, no commands provided for dispatch")
    }

    val commands = commandsList.associateBy { it.name }
    if (args.isEmpty()) {
        errorHandler.error("No arguments supplied.\n\n${getAllCommands(commands)}")
    }
    val commandName = args[0]
    if (commandName.isNullOrBlank()) {
        errorHandler.error(
            "The first argument needs to be specified, null or a blank given." +
                "\n\n${getAllCommands(commands)}"
        )
    }

    val nonBlankArgs = args.asSequence()
        .filterNotNull()
        .filter { it.isNotBlank() }
        .toList()
        .toTypedArray()

    val command = commands[commandName] ?: error("Unknown command supplied.\n\n${getAllCommands(commands)}")

    if (command.numOfArgsNotOk(nonBlankArgs.size)) {
        errorHandler.error(
            """
            |Not enough or too many arguments supplied for command: ${command.name}
            |
            |${expectedArgsAndGiven(command, nonBlankArgs)}
            |
            |Following an example:
            |${command.example}
            """.trimMargin()
        )
    }

    command.execute(nonBlankArgs, errorHandler)

}

private fun getAllCommands(commands: Map<String, ConsoleCommand>): String {
    val sb = StringBuilder("Currently we support the following commands:\n")
    commands.entries.appendToStringBuilder(sb, "\n") { (k, v), sb2 ->
        sb2.append(k.padEnd(15)).append("// ").append(v.description)
    }
    sb.append("\n\n")
    commands.values.appendToStringBuilder(sb, "\n\n") { it, sb2 ->
        sb2.append(it.arguments)
    }
    return sb.toString()
}
