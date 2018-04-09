package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import ch.loewenfels.depgraph.data.serialization.fromJson

private const val MAVEN_PROJECT_ID = "ch.loewenfels.depgraph.data.maven.MavenProjectId"
private const val JENKINS_MAVEN_RELEASE_PLUGIN = "ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin"
private const val JENKINS_MULTI_MAVEN_RELEASE_PLUGIN =
    "ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin"
private const val JENKINS_UPDATE_DEPENDENCY = "ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency"

fun deserialize(body: String): ReleasePlan {
    val releasePlanJson = JSON.parse<ReleasePlanJson>(body)
    val rootProjectId = createProjectId(releasePlanJson.id)
    val projects = deserializeProjects(releasePlanJson)
    val submodules = deserializeMapOfProjectIdAndSetProjectId(releasePlanJson.submodules)
    val dependents = deserializeMapOfProjectIdAndSetProjectId(releasePlanJson.dependents)
    val warnings = releasePlanJson.warnings.toList()
    val infos = releasePlanJson.infos.toList()
    return ReleasePlan(rootProjectId, projects, submodules, dependents, warnings, infos)
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
        map[projectId] = Project(
            projectId, it.isSubmodule, it.currentVersion, it.releaseVersion, it.level,
            deserializeCommands(it.commands),
            it.relativePath
        )
    }
    return map
}

fun deserializeCommands(commands: Array<GenericType<Command>>): List<Command> {

    return commands.map {
        when (it.t) {
            JENKINS_MAVEN_RELEASE_PLUGIN -> createJenkinsMavenReleasePlugin(
                it.p
            )
            JENKINS_MULTI_MAVEN_RELEASE_PLUGIN -> createJenkinsMultiMavenReleasePlugin(
                it.p
            )
            JENKINS_UPDATE_DEPENDENCY -> createJenkinsUpdateDependency(
                it.p
            )
            else -> throw UnsupportedOperationException("${it.t} is not supported.")
        }
    }
}

fun createJenkinsMavenReleasePlugin(command: Command): JenkinsMavenReleasePlugin {
    val it = command.unsafeCast<JenkinsMavenReleasePlugin>()
    return JenkinsMavenReleasePlugin(deserializeState(it), it.nextDevVersion)
}

fun createJenkinsMultiMavenReleasePlugin(command: Command): Command {
    val it = command.unsafeCast<JenkinsMultiMavenReleasePlugin>()
    return JenkinsMultiMavenReleasePlugin(deserializeState(it), it.nextDevVersion)
}

fun createJenkinsUpdateDependency(command: Command): JenkinsUpdateDependency {
    val it = command.unsafeCast<JenkinsUpdateDependency>()
    return JenkinsUpdateDependency(deserializeState(it), MavenProjectId(it.projectId.groupId, it.projectId.artifactId))
}

private fun deserializeState(it: Command): CommandState {
    val json = it.state.unsafeCast<CommandStateJson>()
    fakeEnumsName(json)
    return fromJson(json)
}

private fun fakeEnumsName(json: CommandStateJson) {
    var command: CommandStateJson? = json
    while (command != null) {
        //necessary to fake an enum's name attribute (state is actually a json object and not really a CommandStateJson)
        js("command.state = {name: command.state}")
        command = if (command.state.name == "Deactivated") {
            json.previous
        } else {
            null
        }
    }
}

fun deserializeMapOfProjectIdAndSetProjectId(mapJson: Array<GenericMapEntry<ProjectId, Array<GenericType<ProjectId>>>>): Map<ProjectId, Set<ProjectId>> {
    return mapJson.associateBy(
        { createProjectId(it.k) },
        { it.v.map { createProjectId(it) }.toHashSet() }
    )
}

private fun createMavenProjectId(genericId: GenericType<ProjectId>): MavenProjectId {
    val dynamicId = genericId.p.unsafeCast<MavenProjectId>()
    return MavenProjectId(dynamicId.groupId, dynamicId.artifactId)
}

external interface ReleasePlanJson {
    val id: GenericType<ProjectId>
    val projects: Array<ProjectJson>
    val submodules: Array<GenericMapEntry<ProjectId, Array<GenericType<ProjectId>>>>
    val dependents: Array<GenericMapEntry<ProjectId, Array<GenericType<ProjectId>>>>
    val warnings: Array<String>
    val infos: Array<String>
}

external interface ProjectJson {
    val id: GenericType<ProjectId>
    val isSubmodule: Boolean
    val currentVersion: String
    val releaseVersion: String
    val level: Int
    val commands: Array<GenericType<Command>>
    val relativePath: String
}

external interface GenericType<out T> {
    val t: String
    val p: T
}

external interface GenericMapEntry<out K, out V> {
    val k: GenericType<K>
    val v: V
}
