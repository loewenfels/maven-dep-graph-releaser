package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.creating.Assert
import org.jetbrains.spek.api.dsl.ActionBody


val exampleA = IdAndVersions(MavenProjectId("com.example", "a", "1.1.1-SNAPSHOT"), "1.1.1", "1.1.2-SNAPSHOT")
val exampleB = IdAndVersions(MavenProjectId("com.example", "b", "1.0.1-SNAPSHOT"), "1.0.1", "1.0.2-SNAPSHOT")
val exampleC = IdAndVersions(MavenProjectId("com.example", "c", "3.0.0-SNAPSHOT"), "3.0.0", "3.0.1-SNAPSHOT")

fun ActionBody.assertRootProjectOnlyReleaseAndReady(rootProject: Project, idAndVersions: IdAndVersions) {
    test("its ${Project::id.name} is ${idAndVersions.id}") {
        assert(rootProject.id).toBe(idAndVersions.id)
    }
    test("its ${Project::newVersion.name} is ${idAndVersions.newVersion}") {
        assert(rootProject.newVersion).toBe(idAndVersions.newVersion)
    }
    test("it contains just the ${JenkinsMavenReleasePlugin::class.simpleName} command, which is Ready with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = ${idAndVersions.nextDevVersion}") {
        assert(rootProject) {
            property(subject::commands).containsStrictly({
                isA<JenkinsMavenReleasePlugin> {
                    isStateReady()
                    property(subject::nextDevVersion).toBe(idAndVersions.nextDevVersion)
                }
            })
        }
    }
}

fun ActionBody.assertProjectAWithDependentB(rootProject: Project) {
    assertProjectWithOneDependent(rootProject, exampleA, exampleB)
}

fun ActionBody.assertProjectWithOneDependent(
    rootProject: Project,
    rootProjectIdAndVersions: IdAndVersions,
    dependentIdAndVersions: IdAndVersions
) {
    assertRootProjectOnlyReleaseAndReady(rootProject, rootProjectIdAndVersions)

    test("it has one dependent project $dependentIdAndVersions") {
        assert(rootProject.dependents).containsStrictly({
            idAndNewVersion(dependentIdAndVersions)
        })
    }
    assertWithDependent(rootProject, rootProjectIdAndVersions, dependentIdAndVersions)
}

fun ActionBody.assertWithDependent(rootProject: Project, dependency: IdAndVersions, dependent: IdAndVersions) {
    test("it has 1 dependent with two commands, updateVersion and Release") {
        assert(rootProject.dependents).containsStrictly({
            property(subject::commands).containsStrictly(
                {
                    isA<JenkinsUpdateDependency> {
                        stateWaitingWithDependencies(dependency.id)
                        property(subject::projectId).toBe(dependency.id)
                    }
                },
                {
                    isA<JenkinsMavenReleasePlugin> {
                        stateWaitingWithDependencies(dependency.id)
                        property(subject::nextDevVersion).toBe(dependent.nextDevVersion)
                    }
                }
            )
        })
    }
}
