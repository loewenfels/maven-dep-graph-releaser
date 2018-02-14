package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.api.cc.en_UK.containsStrictly
import ch.tutteli.atrium.api.cc.en_UK.isA
import ch.tutteli.atrium.api.cc.en_UK.property
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.idAndNewVersion
import ch.tutteli.atrium.isStateReady
import ch.tutteli.atrium.stateWaitingWithDependencies
import org.jetbrains.spek.api.dsl.ActionBody

val exampleAProjectId = MavenProjectId("com.example", "a", "1.1.1-SNAPSHOT")
val exampleBProjectId = MavenProjectId("com.example", "b", "1.0.1-SNAPSHOT")

fun ActionBody.assertRootProjectOnlyReleaseAndReady(rootProject: Project, projectId: ProjectId, newVersion: String, nextDevVersion: String) {
    test("its ${Project::id} is $projectId") {
        assert(rootProject.id).toBe(projectId)
    }
    test("its ${Project::newVersion.name} is $newVersion") {
        assert(rootProject.newVersion).toBe(newVersion)
    }
    test("it contains just the ${JenkinsMavenReleasePlugin::class.simpleName} command, which is Ready with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = $newVersion") {
        assert(rootProject) {
            property(subject::commands).containsStrictly({
                isA<JenkinsMavenReleasePlugin> {
                    isStateReady()
                    property(subject::nextDevVersion).toBe(nextDevVersion)
                }
            })
        }
    }
}

fun ActionBody.assertProjectAWithDependentB(rootProject: Project) {
    assertProjectWithOneDependent(
        rootProject,
        exampleAProjectId, "1.1.1", "1.1.2-SNAPSHOT",
        exampleBProjectId, "1.0.1", "1.0.2-SNAPSHOT"
    )
}

fun ActionBody.assertProjectWithOneDependent(
    rootProject: Project,
    rootProjectId: MavenProjectId,
    rootProjectNewVersion: String,
    rootProjectNextDevVersion: String,
    dependentProjectId: MavenProjectId,
    dependentNewVersion: String,
    dependentNextDevVersion: String
) {
    assertRootProjectOnlyReleaseAndReady(rootProject, rootProjectId, rootProjectNewVersion, rootProjectNextDevVersion)

    test("it has one dependent project $dependentProjectId") {
        assert(rootProject.dependents).containsStrictly({
            idAndNewVersion(dependentProjectId, dependentNewVersion)
        })
    }
    test("dependent project has two commands, updateVersion and Release") {
        assert(rootProject.dependents[0].commands).containsStrictly(
            {
                isA<JenkinsUpdateDependency> {
                    stateWaitingWithDependencies(rootProjectId)
                    property(subject::projectId).toBe(rootProjectId)
                }
            },
            {
                isA<JenkinsMavenReleasePlugin> {
                    stateWaitingWithDependencies(rootProjectId)
                    property(subject::nextDevVersion).toBe(dependentNextDevVersion)
                }
            }
        )
    }
}

