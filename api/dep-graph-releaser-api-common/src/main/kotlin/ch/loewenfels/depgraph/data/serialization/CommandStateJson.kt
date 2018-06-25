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
        WAITING,
        READY,
        READY_TO_RE_TRIGGER,
        QUEUEING,
        IN_PROGRESS,
        SUCCEEDED,
        FAILED,
        DEACTIVATED,
        DISABLED
    }
}

fun fromJson(json: CommandStateJson): CommandState = when (json.state) {
    WAITING -> CommandState.Waiting(json.dependencies ?: throwIllegal("dependencies", WAITING.name))
    READY -> CommandState.Ready
    READY_TO_RE_TRIGGER -> CommandState.ReadyToReTrigger
    QUEUEING -> CommandState.Queueing
    IN_PROGRESS -> CommandState.InProgress
    SUCCEEDED -> CommandState.Succeeded
    FAILED -> CommandState.Failed
    DEACTIVATED -> CommandState.Deactivated(fromJson(json.previous ?: throwIllegal("previous", DEACTIVATED.name)))
    DISABLED -> CommandState.Disabled
}

private fun throwIllegal(fieldName: String, stateName: String): Nothing = throw IllegalArgumentException("$fieldName must be defined for state $stateName")
