package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.creating.Assert

fun Assert<Command>.isStateReady() = property(subject::state).toBe(CommandState.Ready)

fun Assert<Command>.stateWaitingWithDependencies(dependency: ProjectId, vararg otherDependencies: ProjectId) = withState<CommandState.Waiting> {
    property(subject::dependencies).contains.inAnyOrder.only.objects(dependency, *otherDependencies)
}

inline fun <reified T : CommandState> Assert<Command>.withState(noinline assertionCreator: Assert<T>.() -> Unit) =
    property(subject::state).isA(assertionCreator)

fun Assert<Command>.isJenkinsUpdateDependencyWaiting(dependency: IdAndVersions) {
    isA<JenkinsUpdateDependency> {
        stateWaitingWithDependencies(dependency.id)
        property(subject::projectId).toBe(dependency.id)
    }
}

fun Assert<Command>.isJenkinsMavenReleaseWaiting(nextDevVersion: String, dependency: IdAndVersions, vararg otherDependencies: IdAndVersions) {
    isA<JenkinsMavenReleasePlugin> {
        stateWaitingWithDependencies(dependency.id, *(otherDependencies.map { it.id }.toTypedArray()))
        property(subject::nextDevVersion).toBe(nextDevVersion)
    }
}

fun Assert<Command>.isJenkinsMultiMavenReleaseWaiting(
    nextDevVersion: String,
    dependency: IdAndVersions,
    otherDependencies: Array<out IdAndVersions>,
    submodule: IdAndVersions,
    vararg otherSubmodules: IdAndVersions
) {
    isA<JenkinsMultiMavenReleasePlugin> {
        stateWaitingWithDependencies(dependency.id, *(otherDependencies.map { it.id }.toTypedArray()))
        property(subject::nextDevVersion).toBe(nextDevVersion)
        property(subject::projects).contains.inAnyOrder.only.objects(submodule.id, *otherSubmodules.map { it.id }.toTypedArray())
    }
}

fun Assert<Command>.isJenkinsUpdateDependencyDeactivated(oldCommand: JenkinsUpdateDependency) {
    isA<JenkinsUpdateDependency> {
        property(subject::state).toBe(CommandState.Deactivated(oldCommand.state))
        property(subject::projectId).toBe(oldCommand.projectId)
    }
}

fun Assert<Command>.isJenkinsMavenReleaseDeactivated(oldCommand: JenkinsMavenReleasePlugin) {
    isA<JenkinsMavenReleasePlugin> {
        property(subject::state).toBe(CommandState.Deactivated(oldCommand.state))
        property(subject::nextDevVersion).toBe(oldCommand.nextDevVersion)
    }
}
