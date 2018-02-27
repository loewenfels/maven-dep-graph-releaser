package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.tutteli.atrium.api.cc.en_UK.*
import org.jetbrains.spek.api.dsl.ActionBody

val exampleA = IdAndVersions(MavenProjectId("com.example", "a"), "1.1.1-SNAPSHOT", "1.1.1", "1.1.2-SNAPSHOT")
val exampleB = IdAndVersions(MavenProjectId("com.example", "b"), "1.0.1-SNAPSHOT", "1.0.1", "1.0.2-SNAPSHOT")
val exampleC = IdAndVersions(MavenProjectId("com.example", "c"), "3.0.0-SNAPSHOT", "3.0.0", "3.0.1-SNAPSHOT")
val exampleD = IdAndVersions(MavenProjectId("com.example", "d"), "4.1-SNAPSHOT", "4.1", "4.2-SNAPSHOT")

fun ActionBody.assertRootProjectOnlyReleaseAndReady(releasePlan: ReleasePlan, idAndVersions: IdAndVersions) {
    test("${ReleasePlan::rootProjectId.name} is expected rootProject") {
        assert(releasePlan.rootProjectId).toBe(idAndVersions.id)
    }
    val rootProject = releasePlan.projects[idAndVersions.id]!!
    test("root project's ${Project::id.name} and versions are $idAndVersions") {
        assert(rootProject) {
            idAndVersions(idAndVersions)
        }
    }
    test("root project's level is 0") {
        assert(rootProject) {
            property(subject::level).toBe(0)
        }
    }
    test("root project contains just the ${JenkinsMavenReleasePlugin::class.simpleName} command, which is Ready with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = ${idAndVersions.nextDevVersion}") {
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

fun ActionBody.assertReleaseAWithDependentBWithDependentC(releasePlan: ReleasePlan, projectB: IdAndVersions = exampleB) {
    assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

    assertWithDependent("root project", releasePlan, exampleA, projectB, 1)
    assertWithDependent("dependent project", releasePlan, projectB, exampleC, 2)

    test("release plan has three projects and three dependents") {
        assert(releasePlan) {
            property(subject::projects).hasSize(3)
            property(subject::dependents).hasSize(3)
        }
    }
}

fun ActionBody.assertProjectAWithDependentB(releasePlan: ReleasePlan) {
    assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

    assertWithDependent("root project", releasePlan, exampleA, exampleB, 1)

    test("release plan has only two projects and two dependents") {
        assert(releasePlan) {
            property(subject::projects).hasSize(2)
            property(subject::dependents).hasSize(2)
        }
    }
}

fun ActionBody.assertWithDependent(projectName: String, releasePlan: ReleasePlan, dependency: IdAndVersions, dependent: IdAndVersions, level: Int) {
    test("$projectName has one dependent") {
        assert(releasePlan).hasDependentsForProject(dependency, dependent)
    }
    test("$projectName's dependent project has two commands, updateVersion and Release") {
        assert(releasePlan.projects[dependent.id]).isNotNull {
            idAndVersions(dependent)
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyWaiting(dependency) },
                { isJenkinsMavenReleaseWaiting(dependent.nextDevVersion, dependency) }
            )
        }
    }
    test("$projectName's dependent project is on level $level") {
        assert(releasePlan.getProject(dependent.id).level).toBe(level)
    }
}
