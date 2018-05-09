package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.tutteli.kbox.mapWithIndex
import org.w3c.dom.HTMLAnchorElement
import kotlin.browser.window
import kotlin.collections.set
import kotlin.js.Promise


class Releaser(
    private val jenkinsUrl: String,
    private val modifiableJson: ModifiableJson,
    private val menu: Menu
) {

    fun release(jobExecutor: JobExecutor): Promise<Boolean> {
        val releasePlan = deserialize(modifiableJson.json)
        checkConfig(releasePlan)
        warnIfNotOnSameHost()

        return release(jobExecutor, releasePlan)
    }

    private fun checkConfig(releasePlan: ReleasePlan) {
        val config = releasePlan.config
        requireConfigEntry(config, ConfigKey.UPDATE_DEPENDENCY_JOB)
        requireConfigEntry(config, ConfigKey.REMOTE_REGEX)
        requireConfigEntry(config, ConfigKey.REMOTE_JOB)
        requireConfigEntry(config, ConfigKey.COMMIT_PREFIX)
    }

    private fun requireConfigEntry(config: Map<ConfigKey, String>, key: ConfigKey) {
        require(config.containsKey(key)) {
            "$key is not defined in settings"
        }
    }

    private fun warnIfNotOnSameHost() {
        val prefix = window.location.protocol + "//" + window.location.hostname
        val isOnSameHost = jenkinsUrl.startsWith(prefix)
        if (!isOnSameHost) {
            showWarning(
                "Remote publish server detected. We currently do not support to consume remote release.json." +
                    "\nThis means that we publish changes during the release process but will not change the location. Thus, please do not reload the page during the release process."
                , 8000
            )
        }
    }

    private fun release(jobExecutor: JobExecutor, releasePlan: ReleasePlan): Promise<Boolean> {
        val project = releasePlan.getRootProject()
        val paramObject = ParamObject(releasePlan, jobExecutor, project, hashMapOf(), hashMapOf())
        Gui.changeReleaseState(ReleaseState.InProgress)
        return releaseProject(paramObject)
            .then {
                val (result, newState) = checkProjectStates(paramObject)
                Gui.changeReleaseState(newState)
                save(paramObject, verbose = false)
                    .catch { t ->
                        showThrowable(
                            Error(
                                "Could not save the release state (changed to $newState)." +
                                    "\nDo not reload if you want to continue using this pipeline and make sure the publisher works as expected." +
                                    "\nMake a change (e.g. change a Release Version) and try to save (will save the changed release state as well) -- do not forget to revert your change and save again.",
                                t
                            )
                        )
                    }
                result
            }
    }

    private fun checkProjectStates(paramObject: ParamObject): Pair<Boolean, ReleaseState> {
        val result = paramObject.projectResults.values.all {
            it === CommandState.Succeeded || it is CommandState.Deactivated || it === CommandState.Disabled
        }
        val newState = if (result) {
            ReleaseState.Succeeded
        } else {
            checkForNoneFailedBug(paramObject)
            ReleaseState.Failed
        }
        return result to newState
    }

    private fun checkForNoneFailedBug(paramObject: ParamObject) {
        if (paramObject.projectResults.values.none { it === CommandState.Failed }) {
            val erroneousProjects = paramObject.projectResults.entries
                .filter { it.value !== CommandState.Failed && it.value !== CommandState.Succeeded }
            if (erroneousProjects.isNotEmpty()) {
                showError(
                    "Seems like there is a bug since no command failed but not all are succeeded." +
                        "\nPlease report a bug, the following projects where affected:" +
                        "\n${erroneousProjects.joinToString("\n") { it.key.identifier }}"
                )
            }
        }
    }

    private fun releaseProject(paramObject: ParamObject): Promise<*> {
        return paramObject.withLockForProject {
            triggerNonReleaseCommandsInclSubmoduleCommands(paramObject).then { jobResult ->
                if (jobResult !== CommandState.Succeeded) return@then jobResult

                triggerReleaseCommands(paramObject).unsafeCast<CommandState>()
            }.then { jobResult ->
                paramObject.projectResults[paramObject.project.id] = jobResult
                if (jobResult !== CommandState.Succeeded) return@then jobResult

                val releasePlan = paramObject.releasePlan
                val allDependents = releasePlan.collectDependentsInclDependentsOfAllSubmodules(paramObject.project.id)
                updateStateWaiting(releasePlan, allDependents)
                releaseDependentProjects(allDependents, releasePlan, paramObject)
            }.catch { t ->
                paramObject.projectResults[paramObject.project.id] = CommandState.Failed
                if (t !== ReleaseFailure) throw t
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
                            dependentProject, index, CommandState.Ready, Gui.STATE_READY
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
    ): Promise<*> {
        val promises: List<Promise<*>> = allDependents
            .asSequence()
            .map { (_, dependentId) -> releasePlan.getProject(dependentId) }
            .filter { !it.isSubmodule }
            .toHashSet()
            .map { dependentProject ->
                releaseProject(ParamObject(paramObject, dependentProject))
            }
        //TODO stops as soon as a bug occurs in the execution of one job, is this ok?
        return Promise.all(promises.toTypedArray())
    }

    private fun triggerNonReleaseCommandsInclSubmoduleCommands(paramObject: ParamObject): Promise<CommandState> {
        return paramObject.project.commands
            .asSequence()
            .mapWithIndex()
            .filter { it.value !is ReleaseCommand }
            .doSequentially(mutableListOf()) { (index, command) ->
                createCommandPromise(paramObject, command, index)
            }.then { jobsResults ->
                paramObject.releasePlan.getSubmodules(paramObject.project.id)
                    .asSequence()
                    .doSequentially(jobsResults as MutableList<CommandState>) { submoduleId ->
                        triggerNonReleaseCommandsInclSubmoduleCommands(ParamObject(paramObject, submoduleId))
                    }
            }.then { jobsResults ->
                jobsResults.firstOrNull { it !== CommandState.Succeeded } ?: CommandState.Succeeded
            }
    }

    private fun <T> Sequence<T>.doSequentially(
        initial: MutableList<CommandState>,
        action: (T) -> Promise<CommandState>
    ): Promise<List<CommandState>> {
        return this.fold(Promise.resolve(initial)) { acc, element ->
            acc.then { list ->
                action(element).then { jobResult ->
                    //do not continue with next command if a previous was not successful
                    if (jobResult === CommandState.Failed) throw ReleaseFailure
                    list.add(jobResult)
                    list
                }
            }.unsafeCast<Promise<MutableList<CommandState>>>()
        }
    }

    private fun triggerReleaseCommands(paramObject: ParamObject): Promise<CommandState> {
        return paramObject.project.commands
            .asSequence()
            .mapWithIndex()
            .filter { it.value is ReleaseCommand }
            .doSequentially(mutableListOf()) { (index, command) ->
                createCommandPromise(paramObject, command, index)
            }.then { jobsResults ->
                jobsResults.firstOrNull { it !== CommandState.Succeeded } ?: CommandState.Succeeded
            }
    }

    private fun createCommandPromise(paramObject: ParamObject, command: Command, index: Int): Promise<CommandState> {
        val state = Gui.getCommandState(paramObject.project.id, index)
        return if (state === CommandState.Ready || state === CommandState.ReadyToReTrigger) {
            triggerCommand(paramObject, command, index)
        } else {
            Promise.resolve(state)
        }
    }

    private fun triggerCommand(paramObject: ParamObject, command: Command, index: Int): Promise<CommandState> {
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
    ): Promise<CommandState> {
        val jobUrl = "$jenkinsUrl/job/${paramObject.getConfig(ConfigKey.UPDATE_DEPENDENCY_JOB)}"
        val jobName = "update dependency of ${paramObject.project.id.identifier}"
        val params = createUpdateDependencyParams(paramObject, command)
        return triggerJob(paramObject, jobUrl, jobName, params, index)
    }

    private fun createUpdateDependencyParams(paramObject: ParamObject, command: JenkinsUpdateDependency): String {
        val dependency = paramObject.releasePlan.getProject(command.projectId)
        val dependencyMavenProjectId = dependency.id as MavenProjectId
        return "pathToProject=${paramObject.project.relativePath}" +
            "&groupId=${dependencyMavenProjectId.groupId}" +
            "&artifactId=${dependencyMavenProjectId.artifactId}" +
            "&newVersion=${dependency.releaseVersion}" +
            "&commitPrefix=${paramObject.getConfig(ConfigKey.COMMIT_PREFIX)}" +
            "&releaseId=${paramObject.releasePlan.releaseId}"
    }

    private fun triggerRelease(paramObject: ParamObject, command: M2ReleaseCommand, index: Int): Promise<CommandState> {
        val (jobUrl, params) = determineJobUrlAndParams(paramObject, command)
        return triggerJob(paramObject, jobUrl, "release ${paramObject.project.id.identifier}", params, index)
    }

    private fun determineJobUrlAndParams(paramObject: ParamObject, command: M2ReleaseCommand): Pair<String, String> {
        val mavenProjectId = paramObject.project.id as MavenProjectId
        val regex = Regex(paramObject.getConfig(ConfigKey.REMOTE_REGEX))
        val relevantParams = paramObject.regexParametersList.asSequence()
            .filter { (regex, _) -> regex.matches(mavenProjectId.identifier) }
            .map { it.second }

        val params = "releaseVersion=${paramObject.project.releaseVersion}" +
            "&nextDevVersion=${command.nextDevVersion}"

        val jobName = paramObject.getJobName()
        return if (regex.matches(paramObject.project.id.identifier)) {
            "$jenkinsUrl/job/${paramObject.getConfig(ConfigKey.REMOTE_JOB)}" to
                "$params&jobName=$jobName&parameters=${relevantParams.joinToString(";")}"
        } else {
            "$jenkinsUrl/job/$jobName" to
                "$params&${relevantParams.joinToString("&")}}"
        }
    }

    private fun triggerJob(
        paramObject: ParamObject,
        jobUrl: String,
        jobName: String,
        params: String,
        index: Int
    ): Promise<CommandState> {
        val project = paramObject.project
        val jobUrlWithSlash = if (jobUrl.endsWith("/")) jobUrl else "$jobUrl/"
        changeCursorToProgress()
        return paramObject.jobExecutor.trigger(jobUrlWithSlash, jobName, params,
            { queuedItemUrl ->
                Gui.changeStateOfCommandAndAddBuildUrl(
                    project, index, CommandState.Queueing, Gui.STATE_QUEUEING, queuedItemUrl
                )
                save(paramObject)
            }, { buildNumber ->
                Gui.changeStateOfCommandAndAddBuildUrl(
                    project, index, CommandState.InProgress, Gui.STATE_IN_PROGRESS, "$jobUrlWithSlash$buildNumber/"
                )
                Promise.resolve(1)
            },
            pollEverySecond = 10,
            maxWaitingTimeForCompleteness = 60 * 15,
            verbose = false
        ).then(
            { CommandState.Succeeded to Gui.STATE_SUCCEEDED },
            { t ->
                showThrowable(Error("Job $jobName failed", t))
                val state = elementById<HTMLAnchorElement>("${Gui.getCommandId(project, index)}${Gui.STATE_SUFFIX}")
                val suffix = "console#footer"
                if (!state.href.endsWith(suffix)) {
                    state.href = state.href + suffix
                }
                CommandState.Failed to Gui.STATE_FAILED
            }
        ).then { (state, message) ->
            Gui.changeStateOfCommand(project, index, state, message)
            changeCursorBackToNormal()
            state
        }
    }

    private fun save(paramObject: ParamObject, verbose: Boolean = false): Promise<Unit> {
        return menu.save(paramObject.jobExecutor, verbose)
            .then { hadChanges ->
                if (!hadChanges) {
                    showWarning("Could not save changes for project ${paramObject.project.id.identifier}. Please report a bug.")
                }
            }
    }

    private data class ParamObject(
        val releasePlan: ReleasePlan,
        val jobExecutor: JobExecutor,
        val project: Project,
        val locks: HashMap<ProjectId, Promise<*>>,
        val projectResults: HashMap<ProjectId, CommandState>
    ) {
        val regexParametersList: List<Pair<Regex, String>>
        private val jobMapping: Map<String, String>

        constructor(paramObject: ParamObject, newProjectId: ProjectId)
            : this(paramObject, paramObject.releasePlan.getProject(newProjectId))

        constructor(paramObject: ParamObject, newProject: Project)
            : this(
            paramObject.releasePlan,
            paramObject.jobExecutor,
            newProject,
            paramObject.locks,
            paramObject.projectResults
        )

        init {
            regexParametersList = parseRegexParameters()
            jobMapping = parseJobMapping()
        }

        private fun parseRegexParameters(): List<Pair<Regex, String>> {
            val regexParameters = getConfig(ConfigKey.REGEX_PARAMS)
            return if (regexParameters.isNotEmpty()) {
                regexParameters.splitToSequence("$")
                    .map { pair ->
                        val index = checkRegexNotEmpty(pair, regexParameters)
                        val parameters = pair.substring(index + 1)
                        checkAtLeastOneParameter(parameters, regexParameters)
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
                "regex requires at least one character.\nregexParameters: $regexParameters"
            }
            return index
        }

        private fun checkAtLeastOneParameter(pair: String, regexParameters: String): Int {
            val index = pair.indexOf('=')
            check(index > 0) {
                "A regexParam requires at least one parameter.\nregexParameters: $regexParameters"
            }
            return index
        }

        private fun parseJobMapping(): Map<String, String> {
            val mapping = getConfig(ConfigKey.JOB_MAPPING)
            return mapping.split("|").associate { pair ->
                val index = pair.indexOf('=')
                check(index > 0) {
                    "At least one mapping has no groupId and artifactId defined.\njobMapping: $mapping"
                }
                val groupIdAndArtifactId = pair.substring(0, index)
                check(groupIdAndArtifactId.contains(':')) {
                    "At least one groupId and artifactId is erroneous, does not contain a `:`.\njobMapping: $mapping"
                }
                val jobName = pair.substring(index + 1)
                check(jobName.isNotBlank()) {
                    "At least one groupId and artifactId is erroneous, has no job name defined.\njobMapping: $mapping"
                }
                groupIdAndArtifactId to jobName
            }
        }


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

        fun getJobName(): String {
            val mavenProjectId = project.id as MavenProjectId
            return jobMapping[mavenProjectId.identifier] ?: mavenProjectId.artifactId
        }
    }

    private object ReleaseFailure : RuntimeException()
}
