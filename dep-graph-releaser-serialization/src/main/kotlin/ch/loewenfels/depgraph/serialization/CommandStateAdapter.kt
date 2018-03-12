package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import ch.loewenfels.depgraph.data.serialization.CommandStateJson.State.*
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Responsible to serialize [CommandState].
 */
object CommandStateAdapter {

    @ToJson
    fun toJson(state: CommandState): CommandStateJson = when (state) {
        is CommandState.Waiting -> CommandStateJson(Waiting, state.dependencies)
        CommandState.Ready -> CommandStateJson(Ready)
        CommandState.InProgress -> CommandStateJson(InProgress)
        CommandState.Succeeded -> CommandStateJson(Succeeded)
        is CommandState.Failed -> CommandStateJson(Failed, state.message)
        is CommandState.Deactivated -> CommandStateJson(Deactivated, toJson(state.previous))
    }

    @FromJson
    fun fromJson(json: CommandStateJson) = ch.loewenfels.depgraph.data.serialization.fromJson(json)
}

