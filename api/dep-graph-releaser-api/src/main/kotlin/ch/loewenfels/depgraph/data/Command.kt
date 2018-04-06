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

    /**
     * Makes a copy of this command but with [CommandState.Disabled] as [state]
     *
     * @throws IllegalStateException in case the state was already [CommandState.Disabled]
     */
    fun asDisabled(): Command {
        check(state !== CommandState.Disabled) {
            "Cannot disable an already disabled command: $this"
        }
        return asNewState(CommandState.Disabled)
    }
}

sealed class CommandState {
    data class Waiting(val dependencies: Set<ProjectId>) : CommandState()
    object Ready : CommandState()
    object InProgress : CommandState()
    object Succeeded : CommandState()
    data class Failed(val message: String) : CommandState()
    data class Deactivated(val previous: CommandState) : CommandState()
    /**
     * Such a command cannot be reactivated in contrast to [Deactivated].
     */
    object Disabled : CommandState()
}
