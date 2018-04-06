package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.ConsoleCommand

fun expectedArgsAndGiven(command: ConsoleCommand, args: Array<out String>) = """
    |${command.arguments}
    |
    |${getGivenArgs(args)}
    """.trimMargin()

fun getGivenArgs(args: Array<out String>) = "Given: ${args.joinToString(" ")}"

internal fun List<String>.toOptionalArgs(
    errorHandler: ErrorHandler,
    vararg params: String
): List<String?> {
    val numOfOptionalArgs = params.size
    if (numOfOptionalArgs < size) {
        errorHandler.error(
            "There are more arguments than specified. " +
                "Expected to create at max $numOfOptionalArgs optional arguments." +
                "\nGiven: ${this.joinToString()}" +
                "\nAccepted optional arguments: ${params.joinToString()}"
        )
    }
    val optionalList = ArrayList<String?>((0 until numOfOptionalArgs).map { null })
    this.forEach { arg ->
        var found = false
        params.forEachIndexed { index, param ->
            if (arg.startsWith(param)) {
                optionalList[index] = arg.substringAfter(param)
                found = true
            }
        }
        if (!found) {
            errorHandler.error(
                "Unknown optional argument passed." +
                    "\nArgument: $arg" +
                    "\nAccepted optional arguments: ${params.joinToString()}"
            )
        }
    }
    return optionalList
}
