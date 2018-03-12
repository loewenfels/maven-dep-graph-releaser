package ch.loewenfels.depgraph.data.serialization

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId

data class CommandStateJson(
    val state: State,
    val message: String?,
    val dependencies: Set<ProjectId>?,
    val previous: CommandStateJson?
) {
    constructor(state: State) : this(state, null, null, null)
    constructor(state: State, message: String) : this(state, message, null, null)
    constructor(state: State, dependencies: Set<ProjectId>) : this(state, null, dependencies, null)
    constructor(state: State, previous: CommandStateJson) : this(state, null, null, previous)
}

enum class State {
    Waiting,
    Ready,
    InProgress,
    Succeeded,
    Failed,
    Deactivated,
}

fun fromJson(json: CommandStateJson): CommandState = when (json.state) {
    State.Waiting -> CommandState.Waiting(json.dependencies ?: throwIllegal("dependencies", "Waiting"))
    State.Ready -> CommandState.Ready
    State.InProgress -> CommandState.InProgress
    State.Succeeded -> CommandState.Succeeded
    State.Failed -> CommandState.Failed(json.message ?: throwIllegal("message", "Failed"))
    State.Deactivated -> CommandState.Deactivated(fromJson(json.previous ?: throwIllegal("previous", "Deactivated")))
}

private fun throwIllegal(fieldName: String, stateName: String): Nothing = throw IllegalArgumentException("$fieldName must be defined for state $stateName")
