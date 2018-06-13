package ch.loewenfels.depgraph.data

import kotlin.reflect.KClass

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
    object ReadyToReTrigger : CommandState()
    /**
     * Command is queued to be executed.
     */
    object Queueing : CommandState()

    object InProgress : CommandState()
    object Succeeded : CommandState()
    object Failed : CommandState()
    data class Deactivated(val previous: CommandState) : CommandState()
    /**
     * Such a command cannot be reactivated in contrast to [Deactivated].
     */
    object Disabled : CommandState()


    fun checkTransitionAllowed(newState: CommandState): CommandState {
        check(this !== Disabled) { "Cannot transition to any state if current state is ${Disabled::class.simpleName}." }
        check(this::class != newState::class) {
            "Cannot transition to the same state as the current." +
                //TODO use $this in stead of $getToStringRepresentation(...) once https://youtrack.jetbrains.com/issue/KT-23970 is fixed
                "\nCurrent: ${this.getToStringRepresentation()}" +
                "\nNew: ${newState.getToStringRepresentation()}"
        }

        when (newState) {

            ReadyToReTrigger -> checkNewStateIsAfter(newState, Failed::class)
            Ready -> {
                checkNewStateIsAfter(newState, Waiting::class)
                if (this is Waiting) { //could also be Deactivated with previous Ready
                    check(this.dependencies.isEmpty()) {
                        "Can only change from ${Waiting::class.simpleName} to ${Ready::class.simpleName} " +
                            "if there are not any dependencies left which we need to wait for." +
                            //TODO use $this in stead of $getToStringRepresentation(...) once https://youtrack.jetbrains.com/issue/KT-23970 is fixed
                            "\nState was: ${this.getToStringRepresentation()}"
                    }
                }
            }
            Queueing -> checkNewStateIsAfter(newState, Ready::class, ReadyToReTrigger::class)
            InProgress -> checkNewStateIsAfter(newState, Queueing::class)
            Succeeded -> checkNewStateIsAfter(newState, InProgress::class)
        }
        return newState
    }

    private fun checkNewStateIsAfter(newState: CommandState, vararg requiredState: KClass<out CommandState>) {
        if (this is Deactivated) {
            check(newState::class == this.previous::class) {
                "Cannot transition to ${newState::class.simpleName} because " +
                    "current state is ${Deactivated::class.simpleName}, can only transition to its previous state." +
                    "\nDeactivated.previous was: ${this.previous.getToStringRepresentation()}"
            }
        } else {
            check(requiredState.any { it.isInstance(this) }) {
                val states = if (requiredState.size == 1) {
                    requiredState[0]::class.simpleName
                } else {
                    "one of: ${requiredState.joinToString { it::class.simpleName!! }}"
                }
                "Cannot transition to ${newState::class.simpleName} because state is not $states." +
                    //TODO use $this in stead of $getToStringRepresentation(...) once https://youtrack.jetbrains.com/issue/KT-23970 is fixed
                    "\nState was: ${this.getToStringRepresentation()}"
            }
        }
    }
}

fun Any.getToStringRepresentation(): String {
    val representation = this.toString()
    return if (representation == "[object Object]") this::class.simpleName!! else representation
}
