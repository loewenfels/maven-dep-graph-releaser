package ch.loewenfels.depgraph.data.serialization

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.serialization.CommandStateJson.State.*

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

    enum class State {
        Waiting,
        Ready,
        InProgress,
        Succeeded,
        Failed,
        Deactivated,
    }
}

fun fromJson(json: CommandStateJson): CommandState = when (json.state) {
    Waiting -> CommandState.Waiting(json.dependencies ?: throwIllegal("dependencies", "Waiting"))
    Ready -> CommandState.Ready
    InProgress -> CommandState.InProgress
    Succeeded -> CommandState.Succeeded
    Failed -> CommandState.Failed(json.message ?: throwIllegal("message", "Failed"))
    Deactivated -> CommandState.Deactivated(fromJson(json.previous ?: throwIllegal("previous", "Deactivated")))
}

private fun throwIllegal(fieldName: String, stateName: String): Nothing = throw IllegalArgumentException("$fieldName must be defined for state $stateName")
