package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.gui.Gui.Companion.changeJobStateFromInProgressTo
import ch.loewenfels.depgraph.gui.Gui.Companion.displayJobState
import ch.loewenfels.depgraph.gui.Gui.Companion.displayJobStateAddLinkToJob
import ch.loewenfels.depgraph.gui.Gui.Companion.stateToCssClass
import kotlin.js.Promise

class Releaser(
    private val jenkinsUrl: String,
    usernameToken: UsernameToken,
    private val modifiableJson: ModifiableJson,
    private val menu: Menu
) {
    private val jobExecutor = JobExecutor(jenkinsUrl, usernameToken)

    fun release(): Promise<Array<out Unit>> {
        val releasePlan = deserialize(modifiableJson.json)
        checkConfig(releasePlan)

        val project = releasePlan.getRootProject()
        val paramObject = ParamObject(releasePlan, project, hashMapOf())
        return releaseProject(paramObject)
    }


    private fun releaseProject(paramObject: ParamObject): Promise<Array<out Unit>> {
        return paramObject.withLockForProject {
            triggerNonReleaseCommandsInclSubmoduleCommands(paramObject).then { arr ->
                //we stop if any command or a command of a submodule was not executed or already succeeded
                if (arr.any { !it }) throw NotReadyState

                triggerReleaseCommands(paramObject)
            }.then {
                val releasePlan = paramObject.releasePlan
                val allDependents =
                    releasePlan.collectDependentsInclDependentsOfAllSubmodules(paramObject.project.id)
                updateStateWaiting(releasePlan, allDependents)
                releaseDependentProjects(allDependents, releasePlan, paramObject)
            }.catch { t ->
                if (t !== NotReadyState) throw t
                arrayOf<Unit>()
            }
        }
    }

    private fun updateStateWaiting(releasePlan: ReleasePlan, allDependents: Set<Pair<ProjectId, ProjectId>>) {
        allDependents.forEach { (multiOrSubmoduleId, dependentId) ->
            releasePlan.getProject(dependentId).commands.forEach { command ->
                val state = command.state
                if (state is CommandState.Waiting && state.dependencies.contains(multiOrSubmoduleId)) {
                    (state.dependencies as MutableSet).remove(multiOrSubmoduleId)
                }
            }
        }
    }

    private fun releaseDependentProjects(
        allDependents: HashSet<Pair<ProjectId, ProjectId>>,
        releasePlan: ReleasePlan,
        paramObject: ParamObject
    ): Promise<Array<*>> {
        val promises: List<Promise<*>> = allDependents
            .asSequence()
            .map { (_, dependentId) -> releasePlan.getProject(dependentId) }
            .filter { !it.isSubmodule }
            .toSet()
            .map { dependentProject ->
                releaseProject(ParamObject(paramObject, dependentProject))
                    .then { _ -> /* we ignore the resulting array on purpose */ }
            }
        return Promise.all(promises.toTypedArray())
    }

    private fun triggerNonReleaseCommandsInclSubmoduleCommands(paramObject: ParamObject): Promise<Promise<List<Boolean>>> {
        return paramObject.project.commands
            .asSequence()
            .mapIndexed { i, t -> i to t }
            .filter { it.second !is ReleaseCommand }
            .fold(Promise.resolve(Promise.resolve(mutableListOf<Boolean>()))) { acc, (index, command) ->
                acc.then { list ->
                    createCommandPromise(paramObject, command, index)
                        .then { result -> list.add(result); list }
                }.unsafeCast<Promise<MutableList<Boolean>>>()
            }
            .then { arr ->
                val initial: Promise<MutableList<Boolean>> = Promise.resolve(arr.toMutableList())
                paramObject.releasePlan.getSubmodules(paramObject.project.id).fold(initial) { acc, submoduleId ->
                    acc.then { list: MutableList<Boolean> ->
                        triggerNonReleaseCommandsInclSubmoduleCommands(ParamObject(paramObject, submoduleId))
                            .then { result -> list.addAll(result); list }
                    }.unsafeCast<Promise<MutableList<Boolean>>>()
                }
            }
    }

    private fun Command.hasStateReadyOrEmptyWaiting(): Boolean {
        val state = state
        return state === ch.loewenfels.depgraph.data.CommandState.Ready ||
            (state is CommandState.Waiting && state.dependencies.isEmpty())
    }

    private fun triggerReleaseCommands(paramObject: ParamObject): Promise<Boolean> {
        return paramObject.project.commands
            .mapIndexed { i, t -> i to t }
            .filter { it.second is ReleaseCommand }
            .fold(Promise.resolve(true)) { acc, (index, command) ->
                acc.then {
                    createCommandPromise(paramObject, command, index)
                        .then { wasReady ->
                            if (!wasReady) throw NotReadyState
                            wasReady
                        }
                }.unsafeCast<Promise<Boolean>>()
            }
    }

    private fun createCommandPromise(paramObject: ParamObject, command: Command, index: Int): Promise<Boolean> {
        return if (command.hasStateReadyOrEmptyWaiting()) {
            triggerCommand(paramObject, command, index)
                .then { true }
        } else {
            Promise.resolve(command.state === CommandState.Succeeded)
        }
    }

    private fun checkConfig(releasePlan: ReleasePlan) {
        val config = releasePlan.config
        requireConfigEntry(config, ConfigKey.UPDATE_DEPENDENCY_JOB)
        requireConfigEntry(config, ConfigKey.REMOTE_REGEX)
        requireConfigEntry(config, ConfigKey.REMOTE_JOB)
    }

    private fun requireConfigEntry(config: Map<ConfigKey, String>, key: ConfigKey) {
        require(config.containsKey(key)) {
            "$key is not defined in settings"
        }
    }

    private fun triggerCommand(paramObject: ParamObject, command: Command, index: Int): Promise<*> {
        return when (command) {
            is JenkinsUpdateDependency -> triggerUpdateDependency(paramObject, command, index)
            is M2ReleaseCommand -> triggerRelease(paramObject, command, index)
            else -> throw UnsupportedOperationException("We do not (yet) support the command: $command")
        }
    }

    private fun triggerUpdateDependency(
        paramObject: ParamObject,
        command: JenkinsUpdateDependency,
        index: Int
    ): Promise<*> {
        changeCursorToProgress()
        val project = paramObject.project
        val jobUrl = "$jenkinsUrl/job/${paramObject.getConfig(ConfigKey.UPDATE_DEPENDENCY_JOB)}"
        val jobName = "update dependency of $project.id"
        val params = createUpdateDependencyParams(paramObject, command)
        return triggerJob(jobUrl, jobName, params, project, index)
    }

    private fun triggerJob(
        jobUrl: String,
        jobName: String,
        params: String,
        project: Project,
        index: Int
    ): Promise<*> {
        val jobUrlWithSlash = if (jobUrl.endsWith("/")) jobUrl else "$jobUrl/"
        displayJobState(project, index, stateToCssClass(CommandState.Ready), "queueing", "Currently queueing the job")
        //jobExecutor.trigger(jobUrlWithSlash, jobName, params, { buildNumber ->
        return sleep(500) { 100 }.then { buildNumber ->
            val command = Gui.getCommand(project, index)
            val buildUrl = "$jobUrlWithSlash$buildNumber/"
            command.asDynamic().buildUrl = buildUrl
            displayJobStateAddLinkToJob(
                project, index, "queueing", CommandState.InProgress, "Job is running", buildUrl
            )
            menu.save(verbose = true)
        }.then {
            sleep(500) {
                true
            }
        }.then(
            {
                changeJobStateFromInProgressTo(
                    project, index, CommandState.Succeeded, "Job completed successfully."
                )
            },
            { t ->
                changeJobStateFromInProgressTo(
                    project, index, CommandState.Failed, "Job failed, click to navigate to the job."
                )
                throw t
            }
        ).finally {
            changeCursorBackToNormal()
        }
    }


    private fun createUpdateDependencyParams(paramObject: ParamObject, command: JenkinsUpdateDependency): String {
        val dependency = paramObject.releasePlan.getProject(command.projectId)
        val dependencyMavenProjectId = dependency.id as MavenProjectId
        return "pathToProject=${paramObject.project.relativePath}" +
            "&groupId=${dependencyMavenProjectId.groupId}" +
            "&artifactId=${dependencyMavenProjectId.artifactId}" +
            "&newVersion=${dependency.releaseVersion}"
    }

    private fun triggerRelease(paramObject: ParamObject, command: M2ReleaseCommand, index: Int): Promise<*> {
        val regex = Regex(paramObject.getConfig(ConfigKey.REMOTE_REGEX))
        val jobUrl = if (regex.matches(paramObject.project.id.identifier)) {
            "$jenkinsUrl/job/${paramObject.getConfig(ConfigKey.REMOTE_JOB)}"
        } else {
            "$jenkinsUrl/job/${(paramObject.project.id as MavenProjectId).artifactId}"
        }
        val jobName = "release ${paramObject.project.id}"
        val params = createReleaseParams(paramObject, command)
        return triggerJob(jobUrl, jobName, params, paramObject.project, index)
    }

    private fun createReleaseParams(paramObject: Releaser.ParamObject, command: M2ReleaseCommand): String {
        //TODO create release params
        return ""
    }

    private data class ParamObject(
        val releasePlan: ReleasePlan,
        val project: Project,
        val locks: HashMap<ProjectId, Promise<*>>
    ) {
        constructor(paramObject: ParamObject, newProjectId: ProjectId)
            : this(paramObject, paramObject.releasePlan.getProject(newProjectId))

        constructor(paramObject: ParamObject, newProject: Project)
            : this(paramObject.releasePlan, newProject, paramObject.locks)

        fun getConfig(configKey: ConfigKey): String {
            return releasePlan.config[configKey] ?: throw IllegalArgumentException("unknown config key: $configKey")
        }

        fun <T> withLockForProject(act: () -> Promise<T>): Promise<T> {
            val projectId = project.id
            val lock = locks[projectId]
            return if (lock == null) {
                val promise = act()
                locks[projectId] = promise
                promise.then { result ->
                    locks.remove(projectId)
                    result
                }
            } else {
                lock.then { withLockForProject(act) }.unsafeCast<Promise<T>>()
            }
        }
    }

    private object NotReadyState : RuntimeException()
}
