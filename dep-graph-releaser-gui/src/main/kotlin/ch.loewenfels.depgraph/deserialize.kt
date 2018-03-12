package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import ch.loewenfels.depgraph.data.serialization.fromJson

private const val MAVEN_PROJECT_ID = "ch.loewenfels.depgraph.data.maven.MavenProjectId"
private const val JENKINS_MAVEN_RELEASE_PLUGIN = "ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin"
private const val JENKINS_UPDATE_DEPENDENCY = "ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency"

fun deserialize(body: String): ReleasePlan {
    val releasePlanJson = JSON.parse<ReleasePlanJson>(body)
    val rootProjectId = createProjectId(releasePlanJson.id)
    val projects = deserializeProjects(releasePlanJson)
    val dependents = deserializeDependents(releasePlanJson)
    return ReleasePlan(rootProjectId, projects, dependents)
}

fun createProjectId(id: GenericType<ProjectId>): ProjectId {
    return when (id.t) {
        MAVEN_PROJECT_ID -> createMavenProjectId(id)
        else -> throw UnsupportedOperationException("${id.t} is not supported.")
    }
}

fun deserializeProjects(releasePlanJson: ReleasePlanJson): Map<ProjectId, Project> {
    val map = hashMapOf<ProjectId, Project>()
    releasePlanJson.projects.forEach {
        val projectId = createProjectId(it.id)
        map[projectId] = Project(projectId, it.currentVersion, it.releaseVersion, it.level, deserializeCommands(it.commands))
    }
    return map
}

fun deserializeCommands(commands: Array<GenericType<Command>>): List<Command> {

    return commands.map {
        when (it.t) {
            JENKINS_MAVEN_RELEASE_PLUGIN -> createJenkinsMavenReleasePlugin(it.p)
            JENKINS_UPDATE_DEPENDENCY -> createJenkinsUpdateDependency(it.p)
            else -> throw UnsupportedOperationException("${it.t} is not supported.")
        }
    }
}

fun createJenkinsUpdateDependency(command: Command): JenkinsUpdateDependency {
    val it = command.unsafeCast<JenkinsUpdateDependency>()
    return JenkinsUpdateDependency(deserializeState(it), MavenProjectId(it.projectId.groupId, it.projectId.artifactId))
}

fun createJenkinsMavenReleasePlugin(command: Command): JenkinsMavenReleasePlugin {
    val it = command.unsafeCast<JenkinsMavenReleasePlugin>()
    return JenkinsMavenReleasePlugin(deserializeState(it), it.nextDevVersion)
}

private fun deserializeState(it: Command): CommandState {
    val json = it.state.unsafeCast<CommandStateJson>()
    js("json.state = {name: json.state}")
    return fromJson(json)
}

fun deserializeDependents(releasePlanJson: ReleasePlanJson): Map<ProjectId, Set<ProjectId>> {
    return releasePlanJson.dependents.associateBy(
        { createProjectId(it.k) },
        { it.v.map { createProjectId(it) }.toSet() }
    )
}

private fun createMavenProjectId(genericId: GenericType<ProjectId>): MavenProjectId {
    val dynamicId = genericId.p.unsafeCast<MavenProjectId>()
    return MavenProjectId(dynamicId.groupId, dynamicId.artifactId)
}

external interface ReleasePlanJson {
    val id: GenericType<ProjectId>
    val projects: Array<ProjectJson>
    val dependents: Array<GenericMapEntry<ProjectId, Array<GenericType<ProjectId>>>>
}

external interface ProjectJson {
    val id: GenericType<ProjectId>
    val currentVersion: String
    val releaseVersion: String
    val level: Int
    val commands: Array<GenericType<Command>>
}

external interface GenericType<out T> {
    val t: String
    val p: T
}

external interface GenericMapEntry<out K, out V> {
    val k: GenericType<K>
    val v: V
}
