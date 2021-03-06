package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.console.ErrorHandler

interface ConsoleCommand {
    val name: String
    val description: String
    val arguments: String
    val example: String

    fun numOfArgsNotOk(number: Int): Boolean
    fun execute(args: Array<out String>, errorHandler: ErrorHandler)
}
