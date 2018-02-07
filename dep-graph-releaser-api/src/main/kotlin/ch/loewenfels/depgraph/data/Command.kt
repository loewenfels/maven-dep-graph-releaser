package ch.loewenfels.depgraph.data

interface Command {
    val id: Int
    val state: CommandState
    val dependent: Set<Int>
}

sealed class CommandState {
    data class Waiting(val dependency: Set<Int>) : CommandState()
    object Ready : CommandState()
    object InProgress : CommandState()
    object Succeeded : CommandState()
    data class Failed(val message: String) : CommandState()
    object Deactivated : CommandState()
}

