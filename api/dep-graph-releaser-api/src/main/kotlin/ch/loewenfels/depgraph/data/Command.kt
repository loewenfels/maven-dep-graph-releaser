package ch.loewenfels.depgraph.data

interface Command {
    val state: CommandState

    /**
     * Makes a copy of this command but with a [newState].
     */
    fun asNewState(newState: CommandState): Command

    /**
     * Makes a copy of this command but with [CommandState.Deactivated] as [state]
     *
     * @throws IllegalStateException in case the state was already [CommandState.Deactivated]
     */
    fun asDeactivated(): Command {
        check(state !is CommandState.Deactivated) {
            "Cannot deactivate an already deactivated command: $this"
        }
        return asNewState(CommandState.Deactivated(state))
    }
}

sealed class CommandState {
    data class Waiting(val dependencies: Set<ProjectId>) : CommandState()
    object Ready : CommandState()
    object InProgress : CommandState()
    object Succeeded : CommandState()
    data class Failed(val message: String) : CommandState()
    data class Deactivated(val previous: CommandState) : CommandState()
}
