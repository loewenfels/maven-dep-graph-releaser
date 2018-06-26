package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Responsible to serialize [CommandState].
 */
object CommandStateAdapter {

    @ToJson
    fun toJson(state: CommandState) = ch.loewenfels.depgraph.data.serialization.toJson(state)

    @FromJson
    fun fromJson(json: CommandStateJson) = ch.loewenfels.depgraph.data.serialization.fromJson(json)
}
