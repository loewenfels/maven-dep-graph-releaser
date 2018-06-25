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
        is CommandState.Waiting -> CommandStateJson(WAITING, state.dependencies)
        is CommandState.Ready -> CommandStateJson(READY)
        is CommandState.ReadyToReTrigger -> CommandStateJson(READY_TO_RE_TRIGGER)
        is CommandState.Queueing -> CommandStateJson(QUEUEING)
        is CommandState.InProgress -> CommandStateJson(IN_PROGRESS)
        is CommandState.Succeeded -> CommandStateJson(SUCCEEDED)
        is CommandState.Failed -> CommandStateJson(FAILED)
        is CommandState.Deactivated -> CommandStateJson(DEACTIVATED, toJson(state.previous))
        is CommandState.Disabled -> CommandStateJson(DISABLED)
    }

    @FromJson
    fun fromJson(json: CommandStateJson) = ch.loewenfels.depgraph.data.serialization.fromJson(json)
}
