package ch.loewenfels.depgraph.data.serialization

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.serialization.CommandStateJson.State.*

data class CommandStateJson(
    var state: State,
    val dependencies: Set<ProjectId>?,
    var previous: CommandStateJson?
) {
    constructor(state: State) : this(state, null, null)
    constructor(state: State, dependencies: Set<ProjectId>) : this(state, dependencies, null)
    constructor(state: State, previous: CommandStateJson) : this(state, null, previous)

    enum class State {
        Waiting,
        Ready,
        InProgress,
        Succeeded,
        Failed,
        Deactivated,
        Disabled
    }
}

fun fromJson(json: CommandStateJson): CommandState = when (json.state) {
    Waiting -> CommandState.Waiting(json.dependencies ?: throwIllegal("dependencies", "Waiting"))
    Ready -> CommandState.Ready
    InProgress -> CommandState.InProgress
    Succeeded -> CommandState.Succeeded
    Failed -> CommandState.Failed
    Deactivated -> CommandState.Deactivated(fromJson(json.previous ?: throwIllegal("previous", "Deactivated")))
    Disabled -> CommandState.Disabled
}

private fun throwIllegal(fieldName: String, stateName: String): Nothing = throw IllegalArgumentException("$fieldName must be defined for state $stateName")
