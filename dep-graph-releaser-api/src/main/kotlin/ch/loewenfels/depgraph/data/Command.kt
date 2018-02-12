package ch.loewenfels.depgraph.data

interface Command {
    val state: CommandState
}

sealed class CommandState {
    data class Waiting(val dependencies: Set<ProjectId>) : CommandState()
    object Ready : CommandState()
    object InProgress : CommandState()
    object Succeeded : CommandState()
    data class Failed(val message: String) : CommandState()
    object Deactivated : CommandState()
}
