package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.ConsoleCommand

fun expectedArgsAndGiven(command: ConsoleCommand, args: Array<out String>) = """
    |${command.arguments}
    |
    |${getGivenArgs(args)}
    """.trimMargin()

fun getGivenArgs(args: Array<out String>) = "Given: ${args.joinToString(" ")}"

fun toOptionalArgs(list: List<String>, number: Int): List<String?> {
    val optionalList = ArrayList<String?>(number)
    optionalList.addAll(list)
    for(i in list.size until number){
        optionalList.add(null)
    }
    return optionalList
}
