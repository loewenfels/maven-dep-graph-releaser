package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.ConsoleCommand

fun expectedArgsAndGiven(command: ConsoleCommand, args: Array<out String>) = """
    |${command.arguments}
    |
    |${getGivenArgs(args)}
    """.trimMargin()

fun getGivenArgs(args: Array<out String>) = "Given: ${args.joinToString(" ")}"

fun List<String>.toOptionalArgs(number: Int): List<String?> {
    require(size <= number) {
        "There are more arguments than specified. Expected to create at max $number optional arguments." +
            "\nGiven: ${this.joinToString(",")}"
    }
    val optionalList = ArrayList<String?>(number)
    optionalList.addAll(this)
    for (i in size until number) {
        optionalList.add(null)
    }
    return optionalList
}
