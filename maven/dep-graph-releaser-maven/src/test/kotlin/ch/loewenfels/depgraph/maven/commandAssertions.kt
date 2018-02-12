package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.creating.Assert

fun Assert<Command>.stateWaitingWithDependencies(dependency: ProjectId, vararg otherDependencies: ProjectId)
    = withState<CommandState.Waiting> {
        property(subject::dependencies).contains.inAnyOrder.only.objects(dependency, *otherDependencies)
    }

inline fun <reified T : CommandState> Assert<Command>.withState(noinline assertionCreator: Assert<T>.() -> Unit) =
    property(subject::state).isA(assertionCreator)
