package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import ch.loewenfels.depgraph.data.serialization.State
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Responsible to serialize [CommandState].
 */
object CommandStateAdapter {

    @ToJson
    fun toJson(state: CommandState): CommandStateJson = when (state) {
        is CommandState.Waiting -> CommandStateJson(State.Waiting, state.dependencies)
        CommandState.Ready -> CommandStateJson(State.Ready)
        CommandState.InProgress -> CommandStateJson(State.InProgress)
        CommandState.Succeeded -> CommandStateJson(State.Succeeded)
        is CommandState.Failed -> CommandStateJson(State.Failed, state.message)
        is CommandState.Deactivated -> CommandStateJson(State.Deactivated, toJson(state.previous))
    }

    @FromJson
    fun fromJson(json: CommandStateJson): CommandState
        = ch.loewenfels.depgraph.data.serialization.fromJson(json)

    private fun throwIllegal(fieldName: String, stateName: String): Nothing = throw IllegalArgumentException("$fieldName must be defined for state $stateName")
}

