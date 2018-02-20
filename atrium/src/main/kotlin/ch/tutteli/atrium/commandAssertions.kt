package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.creating.Assert

fun Assert<Command>.isStateReady() = property(subject::state).toBe(CommandState.Ready)

fun Assert<Command>.stateWaitingWithDependencies(dependency: ProjectId, vararg otherDependencies: ProjectId) = withState<CommandState.Waiting> {
    property(subject::dependencies).contains.inAnyOrder.only.objects(dependency, *otherDependencies)
}

inline fun <reified T : CommandState> Assert<Command>.withState(noinline assertionCreator: Assert<T>.() -> Unit) =
    property(subject::state).isA(assertionCreator)

fun Assert<Command>.isJenkinsUpdateDependency(dependency: IdAndVersions) {
    isA<JenkinsUpdateDependency> {
        stateWaitingWithDependencies(dependency.id)
        property(subject::projectId).toBe(dependency.id)
    }
}

fun Assert<Command>.isJenkinsMavenReleaseWithDependency(nextDevVersion: String, dependency: IdAndVersions, vararg otherDependencies: IdAndVersions) {
    isA<JenkinsMavenReleasePlugin> {
        stateWaitingWithDependencies(dependency.id, *(otherDependencies.map { it.id }.toTypedArray()))
        property(subject::nextDevVersion).toBe(nextDevVersion)
    }
}
