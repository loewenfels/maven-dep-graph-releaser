package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import kotlin.collections.set
import kotlin.js.Promise


class Releaser(
    private val jenkinsUrl: String,
    private val modifiableJson: ModifiableJson,
    private val menu: Menu
) {

    fun release(jobExecutor: JobExecutor): Promise<Array<out Unit>> {
        val releasePlan = deserialize(modifiableJson.json)
        checkConfig(releasePlan)

        val project = releasePlan.getRootProject()
        val paramObject = ParamObject(releasePlan, jobExecutor, project, hashMapOf())
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
            val dependentProject = releasePlan.getProject(dependentId)
            dependentProject.commands.forEachIndexed { index, _ ->
                val state = Gui.getCommandState(dependentId, index)
                if (state is CommandState.Waiting && state.dependencies.contains(multiOrSubmoduleId)) {
                    (state.dependencies as MutableSet).remove(multiOrSubmoduleId)
                    if (state.dependencies.isEmpty()) {
                        Gui.changeStateOfCommand(
                            dependentProject, index, CommandState.Ready, "Ready to be queued for execution."
                        )
                    }
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
            .toHashSet()
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
        val state = Gui.getCommandState(paramObject.project.id, index)
        return if (state === CommandState.Ready) {
            triggerCommand(paramObject, command, index)
                .then { true }
        } else {
            Promise.resolve(state === CommandState.Succeeded)
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
        val jobUrl = "$jenkinsUrl/job/${paramObject.getConfig(ConfigKey.UPDATE_DEPENDENCY_JOB)}"
        val jobName = "update dependency of ${paramObject.project.id.identifier}"
        val params = createUpdateDependencyParams(paramObject, command)
        return triggerJob(paramObject, jobUrl, jobName, params, index)
    }

    private fun triggerJob(
        paramObject: ParamObject,
        jobUrl: String,
        jobName: String,
        params: String,
        index: Int
    ): Promise<*> {
        val project = paramObject.project
        console.log("trigger: ${project.id.identifier} / $jobUrl / $params")
        val jobUrlWithSlash = if (jobUrl.endsWith("/")) jobUrl else "$jobUrl/"
        changeCursorToProgress()
        return paramObject.jobExecutor.trigger(jobUrlWithSlash, jobName, params,
            { queuedItemUrl ->
                Gui.changeStateOfCommandAndAddBuildUrl(
                    project, index, CommandState.Queueing, "Currently queueing the job.", queuedItemUrl
                )
                save(paramObject)
            }, { buildNumber ->
                Gui.changeStateOfCommandAndAddBuildUrl(
                    project, index, CommandState.InProgress, "Job is running.", "$jobUrlWithSlash$buildNumber/"
                )
                save(paramObject)
            },
            verbose = false
        ).then {
            Gui.changeStateOfCommand(
                project, index, CommandState.Succeeded, "Job completed successfully."
            )
        }.catch { t ->
            Gui.changeStateOfCommand(
                project, index, CommandState.Failed, "Job failed, click to navigate to the job."
            )
            throw t
        }.finally {
            changeCursorBackToNormal()
        }
    }

    private fun save(paramObject: ParamObject): Promise<Unit> {
        return menu.save(paramObject.jobExecutor, verbose = false).then { hadChanges ->
            if (!hadChanges) {
                showWarning("Could not save changes for project ${paramObject.project.id.identifier}. Please report a bug.")
            }
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
        val (jobUrl, params) = determineJobUrlAndParams(paramObject, command)
        return triggerJob(paramObject, jobUrl, "release ${paramObject.project.id}", params, index)
    }

    private fun determineJobUrlAndParams(paramObject: ParamObject, command: M2ReleaseCommand): Pair<String, String> {
        val mavenProjectId = paramObject.project.id as MavenProjectId
        val regex = Regex(paramObject.getConfig(ConfigKey.REMOTE_REGEX))

        val regexParameters = paramObject.getConfig(ConfigKey.REGEX_PARAMS)
        val regexParametersList = parseRegexParametersList(regexParameters)
        val relevantParams = regexParametersList.asSequence()
            .filter { (regex, _) -> regex.matches(mavenProjectId.identifier) }
            .map { it.second }

        val params = "releaseVersion=${paramObject.project.releaseVersion}" +
            "&nextDevVersion=${command.nextDevVersion}"

        return if (regex.matches(paramObject.project.id.identifier)) {
            "$jenkinsUrl/job/${paramObject.getConfig(ConfigKey.REMOTE_JOB)}" to
                "$params&jobName=${mavenProjectId.artifactId}&parameters=${relevantParams.joinToString(";")}"
        } else {
            "$jenkinsUrl/job/$mavenProjectId" to
                "$params&${relevantParams.joinToString("&")}}"
        }
    }

    private fun parseRegexParametersList(regexParameters: String): List<Pair<Regex, String>> {
        return if (regexParameters.isNotEmpty()) {
            regexParameters.splitToSequence("$")
                .map { pair ->
                    val index = checkRegexNotEmpty(pair, regexParameters)
                    val parameters = pair.substring(index + 1)
                    checkParamNameNotEmpty(parameters, regexParameters)
                    Regex(pair.substring(0, index)) to parameters
                }
                .toList()
        } else {
            emptyList()
        }
    }

    private fun checkRegexNotEmpty(pair: String, regexParameters: String): Int {
        val index = pair.indexOf('#')
        check(index > 0) {
            "regex requires at least one character.\nParameters: $regexParameters"
        }
        return index
    }

    private fun checkParamNameNotEmpty(pair: String, parameters: String): Int {
        val index = pair.indexOf('=')
        check(index > 0) {
            "Parameter name requires at least one character.\nParameters: $parameters"
        }
        return index
    }

    private data class ParamObject(
        val releasePlan: ReleasePlan,
        val jobExecutor: JobExecutor,
        val project: Project,
        val locks: HashMap<ProjectId, Promise<*>>
    ) {
        constructor(paramObject: ParamObject, newProjectId: ProjectId)
            : this(paramObject, paramObject.releasePlan.getProject(newProjectId))

        constructor(paramObject: ParamObject, newProject: Project)
            : this(paramObject.releasePlan, paramObject.jobExecutor, newProject, paramObject.locks)

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
