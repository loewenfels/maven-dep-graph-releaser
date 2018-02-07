package ch.loewenfels.depgraph.data

data class Command(
    val id: Int,
    val type: CommandType,
    val state: CommandState,
    val dependent: Set<Int>
)

/**
 * A marker interface for different types of [Command]s.
 *
 * An interface on purpose so that we are open for extensions.
 */
interface CommandType

sealed class CommandState {
    class WAITING(val dependency: Set<Int>) : CommandState()
    object READY : CommandState()
    object IN_PROGRESS : CommandState()
    object SUCCEDED : CommandState()
    class FAILED(val message: String) : CommandState()
    object DEACTIVATED : CommandState()
}

