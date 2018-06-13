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
        is CommandState.Ready -> CommandStateJson(Ready)
        is CommandState.ReadyToReTrigger -> CommandStateJson(ReadyToReTrigger)
        is CommandState.Queueing -> CommandStateJson(Queueing)
        is CommandState.InProgress -> CommandStateJson(InProgress)
        is CommandState.Succeeded -> CommandStateJson(Succeeded)
        is CommandState.Failed -> CommandStateJson(Failed)
        is CommandState.Deactivated -> CommandStateJson(Deactivated, toJson(state.previous))
        is CommandState.Disabled -> CommandStateJson(Disabled)
    }

    @FromJson
    fun fromJson(json: CommandStateJson) = ch.loewenfels.depgraph.data.serialization.fromJson(json)
}
