package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Responsible to serialize [CommandState].
 */
object CommandStateAdapter {

    @ToJson fun toJson(state: CommandState): CommandStateJson = when (state) {
        is CommandState.Waiting -> CommandStateJson(State.Waiting, state.dependencies)
        CommandState.Ready -> CommandStateJson(State.Ready)
        CommandState.InProgress -> CommandStateJson(State.InProgress)
        CommandState.Succeeded -> CommandStateJson(State.Succeeded)
        is CommandState.Failed -> CommandStateJson(State.Failed, state.message)
        CommandState.Deactivated -> CommandStateJson(State.Deactivated)
    }

    @FromJson fun fromJson(json: CommandStateJson): CommandState = when (json.state) {
        State.Waiting -> CommandState.Waiting(json.dependencies ?: throwIllegal("dependencies", "Waiting"))
        State.Ready -> CommandState.Ready
        State.InProgress -> CommandState.InProgress
        State.Succeeded -> CommandState.Succeeded
        State.Failed -> CommandState.Failed(json.message ?: throwIllegal("message", "Failed"))
        State.Deactivated -> CommandState.Deactivated
    }

    private fun throwIllegal(fieldName: String, stateName: String): Nothing = throw IllegalArgumentException("$fieldName must be defined for state $stateName")
}

data class CommandStateJson(val state: State, val message: String?, val dependencies: Set<ProjectId>?) {
    constructor(state: State) : this(state, null, null)
    constructor(state: State, message: String) : this(state, message, null)
    constructor(state: State, dependencies: Set<ProjectId>?) : this(state, null, dependencies)
}

enum class State {
    Waiting,
    Ready,
    InProgress,
    Succeeded,
    Failed,
    Deactivated,
}
