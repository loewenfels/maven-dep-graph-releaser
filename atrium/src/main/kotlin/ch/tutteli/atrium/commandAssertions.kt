package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.creating.Assert
import ch.tutteli.atrium.creating.AssertionPlant

fun Assert<Command>.isStateReady() = property(subject::state).toBe(CommandState.Ready)

fun Assert<Command>.withStateWaitingWithDependencies(dependency: ProjectId, vararg otherDependencies: ProjectId) =
    property(subject::state).isA<CommandState.Waiting> {
        withDependencies(dependency, *otherDependencies)
    }

fun Assert<CommandState.Waiting>.withDependencies(
    dependency: ProjectId,
    vararg otherDependencies: ProjectId
) {
    property(subject::dependencies).contains.inAnyOrder.only.objects(dependency, *otherDependencies)
}

fun Assert<Command>.isJenkinsUpdateDependencyWaiting(dependency: IdAndVersions) {
    isA<JenkinsUpdateDependency> {
        withStateWaitingWithDependencies(dependency.id)
        property(subject::projectId).toBe(dependency.id)
    }
}

fun Assert<Command>.isJenkinsUpdateDependencyDeactivatedWaiting(dependency: IdAndVersions) {
    isA<JenkinsUpdateDependency> {
        property(subject::state).isA<CommandState.Deactivated> {
            previousIsStateWaiting(dependency.id)
        }
        property(subject::projectId).toBe(dependency.id)
    }
}

fun AssertionPlant<CommandState.Deactivated>.previousIsStateWaiting(
    dependency: ProjectId,
    vararg otherDependencies: ProjectId
) {
    property(subject::previous) {
        isA<CommandState.Waiting> {
            withDependencies(dependency, *otherDependencies)
        }
    }
}

fun Assert<Command>.isJenkinsMavenReleaseWaiting(
    nextDevVersion: String,
    dependency: IdAndVersions,
    vararg otherDependencies: IdAndVersions
) {
    isA<JenkinsMavenReleasePlugin> {
        withStateWaitingWithDependencies(dependency.id, *otherDependencies.mapToProjectIds())
        property(subject::nextDevVersion).toBe(nextDevVersion)
    }
}

fun Assert<Command>.isJenkinsMavenReleaseDeactivatedWaiting(
    nextDevVersion: String,
    dependency: IdAndVersions,
    vararg otherDependencies: IdAndVersions
) {
    isA<JenkinsMavenReleasePlugin> {
        property(subject::state).isA<CommandState.Deactivated> {
            previousIsStateWaiting(dependency.id, *otherDependencies.mapToProjectIds())
        }
        property(subject::nextDevVersion).toBe(nextDevVersion)
    }
}

fun Assert<Command>.isJenkinsMavenReleaseDisabled(nextDevVersion: String) {
    isA<JenkinsMavenReleasePlugin> {
        property(subject::state).toBe(CommandState.Disabled)
        property(subject::nextDevVersion).toBe(nextDevVersion)
    }
}


fun Assert<Command>.isJenkinsMultiMavenReleaseWaiting(
    nextDevVersion: String,
    dependency: IdAndVersions,
    vararg otherDependencies: IdAndVersions
) {
    isA<JenkinsMultiMavenReleasePlugin> {
        withStateWaitingWithDependencies(dependency.id, *otherDependencies.mapToProjectIds())
        property(subject::nextDevVersion).toBe(nextDevVersion)
    }
}

fun Assert<Command>.isJenkinsUpdateDependencyDeactivated(oldCommand: JenkinsUpdateDependency) {
    isA<JenkinsUpdateDependency> {
        property(subject::state).toBe(CommandState.Deactivated(oldCommand.state))
        property(subject::projectId).toBe(oldCommand.projectId)
    }
}

fun Assert<Command>.isJenkinsUpdateDependencyDisabled(oldCommand: JenkinsUpdateDependency) {
    isA<JenkinsUpdateDependency> {
        property(subject::state).toBe(CommandState.Disabled)
        property(subject::projectId).toBe(oldCommand.projectId)
    }
}

fun Assert<Command>.isJenkinsMavenReleaseDeactivated(oldCommand: JenkinsMavenReleasePlugin) {
    isA<JenkinsMavenReleasePlugin> {
        property(subject::state).toBe(CommandState.Deactivated(oldCommand.state))
        property(subject::nextDevVersion).toBe(oldCommand.nextDevVersion)
    }
}
