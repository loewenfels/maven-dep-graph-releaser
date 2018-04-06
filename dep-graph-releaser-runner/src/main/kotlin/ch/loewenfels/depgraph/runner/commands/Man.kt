package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.toOptionalArgs
import ch.tutteli.kbox.appendToStringBuilder

class Man(private val commands: Map<String, ConsoleCommand>) : ConsoleCommand {

    companion object {
        private const val COMMAND = "-command="
    }
    override val name = "man"
    override val description = "shows kind of a man page of one or all available commands"
    override val example = "./produce $name -command=html"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |(${COMMAND}json)   // optionally: the command to show, shows all if not provided
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number < 1 || number > 2

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (commandName) = args.drop(1).toOptionalArgs(errorHandler, COMMAND)
        if (commandName != null) {
            val command = getCommand(commandName, errorHandler)
            println(command.arguments)
        }else{
            println(showAllCommands())
        }
    }

    fun showAllCommands(): String {
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

    fun getCommand(commandName: String, errorHandler: ErrorHandler): ConsoleCommand {
        return commands[commandName] ?: errorHandler.error("Unknown command supplied.\nCommand name: $commandName\n${showAllCommands()}")
    }

}
