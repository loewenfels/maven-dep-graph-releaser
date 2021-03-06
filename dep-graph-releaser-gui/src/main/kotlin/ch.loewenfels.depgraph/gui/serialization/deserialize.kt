package ch.loewenfels.depgraph.gui.serialization

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsSingleMavenReleaseCommand
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import ch.loewenfels.depgraph.data.serialization.fromJson
import ch.loewenfels.depgraph.gui.components.Messages.Companion.showWarning

fun deserialize(body: String): ReleasePlan {
    val releasePlanJson = JSON.parse<ReleasePlanJson>(body)
    val state = deserializeReleaseState(releasePlanJson)
    val typeOfRun = deserializeTypeOfRun(releasePlanJson)
    val rootProjectId = deserializeProjectId(releasePlanJson.id)
    val projects = deserializeProjects(releasePlanJson)
    val submodules = deserializeMapOfProjectIdAndSetProjectId(releasePlanJson.submodules)
    val dependents = deserializeMapOfProjectIdAndSetProjectId(releasePlanJson.dependents)
    val warnings = releasePlanJson.warnings.toList()
    val infos = releasePlanJson.infos.toList()
    val config = deserializeConfig(releasePlanJson.config)

    return ReleasePlan(
        releasePlanJson.releaseId,
        state,
        typeOfRun,
        rootProjectId,
        projects,
        submodules,
        dependents,
        warnings,
        infos,
        config
    )
}

fun deserializeReleaseState(releasePlanJson: ReleasePlanJson): ReleaseState =
    ReleaseState.valueOf(releasePlanJson.state.unsafeCast<String>())

fun deserializeTypeOfRun(releasePlanJson: ReleasePlanJson): TypeOfRun =
    TypeOfRun.valueOf(releasePlanJson.typeOfRun.unsafeCast<String>())

fun deserializeProjectId(id: GenericType<ProjectId>): ProjectId = when (id.t) {
    MavenProjectId.TYPE_ID -> createMavenProjectId(id)
    else -> throw UnsupportedOperationException("${id.t} is not a supported ProjectId type.")
}

private fun createMavenProjectId(genericId: GenericType<ProjectId>): MavenProjectId {
    val dynamicId = genericId.p.unsafeCast<MavenProjectId>()
    return MavenProjectId(dynamicId.groupId, dynamicId.artifactId)
}

fun deserializeProjects(releasePlanJson: ReleasePlanJson): Map<ProjectId, Project> {
    val map = hashMapOf<ProjectId, Project>()
    releasePlanJson.projects.forEach {
        val projectId = deserializeProjectId(it.id)
        map[projectId] = Project(
            projectId, it.isSubmodule, it.currentVersion, it.releaseVersion, it.level,
            it.commands.map { deserializeCommand(it) },
            it.relativePath
        )
    }
    return map
}

fun deserializeCommand(it: GenericType<Command>): Command {
    //TODO if we want to support custom commands then we have to come up with a ServiceLoader for JS as well
    return when (it.t) {
        JenkinsSingleMavenReleaseCommand.TYPE_ID -> createJenkinsMavenReleasePlugin(it.p)
        JenkinsMultiMavenReleasePlugin.TYPE_ID -> createJenkinsMultiMavenReleasePlugin(it.p)
        JenkinsUpdateDependency.TYPE_ID -> createJenkinsUpdateDependency(it.p)
        else -> throw UnsupportedOperationException("${it.t} is not a supported Command type.")
    }
}

fun createJenkinsMavenReleasePlugin(command: Command): JenkinsSingleMavenReleaseCommand {
    val it = command.unsafeCast<JenkinsSingleMavenReleaseCommand>()
    return JenkinsSingleMavenReleaseCommand(deserializeCommandState(it), it.nextDevVersion, it.buildUrl)
}

fun createJenkinsMultiMavenReleasePlugin(command: Command): Command {
    val it = command.unsafeCast<JenkinsMultiMavenReleasePlugin>()
    return JenkinsMultiMavenReleasePlugin(deserializeCommandState(it), it.nextDevVersion, it.buildUrl)
}

fun createJenkinsUpdateDependency(command: Command): JenkinsUpdateDependency {
    val it = command.unsafeCast<JenkinsUpdateDependency>()
    val projectId = MavenProjectId(it.projectId.groupId, it.projectId.artifactId)
    return JenkinsUpdateDependency(deserializeCommandState(it), projectId, it.buildUrl)
}

fun deserializeCommandState(it: Command): CommandState {
    val json = it.state.unsafeCast<CommandStateJson>()
    val fixedState = fakeEnumsName(json)
    val state = fromJson(fixedState)
    val tmpState = (state as? CommandState.Deactivated)?.previous ?: state
    if (tmpState is CommandState.Waiting) {
        @Suppress("UNCHECKED_CAST")
        val realDependencies = tmpState.dependencies as Array<GenericType<ProjectId>>
        val deserializedDependencies = realDependencies.map {
            deserializeProjectId(it)
        }.toHashSet()
        tmpState.asDynamic().dependencies = deserializedDependencies
    }
    return state
}

private fun fakeEnumsName(json: CommandStateJson): CommandStateJson {
    val state = JSON.parse<CommandStateJson>(JSON.stringify(json))
    var tmp: CommandStateJson? = state
    while (tmp != null) {
        //necessary to fake an enum's name attribute (state is actually a json object and not really a CommandStateJson)
        js("tmp.state = {name: tmp.state}")
        tmp = when (tmp.state.name) {
            CommandStateJson.State.DEACTIVATED.name,
            CommandStateJson.State.TIMEOUT.name -> tmp.previous
            else -> null
        }
    }
    return state
}

fun deserializeMapOfProjectIdAndSetProjectId(
    mapJson: Array<GenericMapEntry<ProjectId, Array<GenericType<ProjectId>>>>
): Map<ProjectId, Set<ProjectId>> = mapJson.associateBy(
    { deserializeProjectId(it.k) },
    { it.v.map { deserializeProjectId(it) }.toHashSet() }
)


fun deserializeConfig(config: Array<Array<String>>): Map<ConfigKey, String> {
    return config.associate {
        if (it.size != 2) {
            showWarning("corrupt config found, size != 2: $it")
        }
        ConfigKey.fromString(it[0]) to it[1]
    }
}


external interface ReleasePlanJson {
    var releaseId: String
    var state: ReleaseState
    var typeOfRun: TypeOfRun
    val id: GenericType<ProjectId>
    val projects: Array<ProjectJson>
    val submodules: Array<GenericMapEntry<ProjectId, Array<GenericType<ProjectId>>>>
    val dependents: Array<GenericMapEntry<ProjectId, Array<GenericType<ProjectId>>>>
    val warnings: Array<String>
    val infos: Array<String>
    val config: Array<Array<String>>
}

external interface ProjectJson {
    val id: GenericType<ProjectId>
    val isSubmodule: Boolean
    val currentVersion: String
    var releaseVersion: String
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
