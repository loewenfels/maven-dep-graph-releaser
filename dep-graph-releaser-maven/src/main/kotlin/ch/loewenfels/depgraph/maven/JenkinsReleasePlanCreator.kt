package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.LevelIterator
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.kbox.appendToStringBuilder
import java.util.logging.Logger

class JenkinsReleasePlanCreator(
    private val versionDeterminer: VersionDeterminer,
    private val options: Options
) {
    private val logger = Logger.getLogger(Analyser::class.qualifiedName)

    fun create(projectToRelease: MavenProjectId, analyser: Analyser): ReleasePlan {
        val currentVersion = analyser.getCurrentVersion(projectToRelease)
        require(currentVersion != null) {
            """Can only release a project which is part of the analysis.
                |Given: ${projectToRelease.identifier}
                |Analysed projects: ${analyser.getAnalysedProjectsAsString()}
            """.trimMargin()
        }

        require(!analyser.isSubmodule(projectToRelease)) {
            """Cannot release a submodule, the given project is part of a multi-module hierarchy
                |Given: $projectToRelease
                |Multi modules: ${analyser.getMultiModules(projectToRelease).joinToString(",")}
            """.trimMargin()
        }

        val rootProject = createRootProject(analyser, projectToRelease, currentVersion)
        val paramObject = createDependents(analyser, rootProject)

        val warnings = mutableListOf<String>()
        reportCyclicDependencies(paramObject, warnings)
        warnings.addAll(analyser.getErroneousPomFiles())

        val infos = mutableListOf<String>()
        reportInterModuleCyclicDependencies(paramObject, infos)

        return ReleasePlan(
            rootProject.id,
            paramObject.projects,
            paramObject.submodules,
            paramObject.dependents,
            warnings,
            infos
        )
    }

    private fun createRootProject(
        analyser: Analyser,
        projectToRelease: MavenProjectId,
        currentVersion: String?
    ): Project {
        val commands = mutableListOf(
            createJenkinsReleasePlugin(
                analyser, projectToRelease, currentVersion!!, CommandState.Ready
            )
        )
        return createInitialProject(projectToRelease, false, currentVersion, 0, commands)
    }

    private fun createInitialProject(
        projectId: MavenProjectId,
        isSubmodule: Boolean,
        currentVersion: String,
        level: Int,
        commands: List<Command>
    ) = Project(
        projectId,
        isSubmodule,
        currentVersion,
        versionDeterminer.releaseVersion(currentVersion),
        level,
        commands
    )

    private fun createJenkinsReleasePlugin(
        analyser: Analyser,
        projectId: MavenProjectId,
        currentVersion: String,
        state: CommandState
    ): Command {
        val definitiveState = if (options.disableReleaseFor.matches(projectId.identifier)) {
            logger.info("Deactivate ${projectId.identifier} due to the specified disableReleaseFor regex: ${options.disableReleaseFor.pattern}")
            CommandState.Disabled
        } else {
            state
        }
        val nextDevVersion = versionDeterminer.nextDevVersion(currentVersion)
        return if (analyser.hasSubmodules(projectId)) {
            JenkinsMultiMavenReleasePlugin(definitiveState, nextDevVersion)
        } else {
            JenkinsMavenReleasePlugin(definitiveState, nextDevVersion)
        }
    }

    private fun createDependents(analyser: Analyser, rootProject: Project): ParamObject {
        val paramObject = ParamObject(analyser, rootProject)
        while(paramObject.levelIterator.hasNext()){
            val dependent = paramObject.levelIterator.next()
            paramObject.submodules[dependent.id] = analyser.getSubmodules(dependent.id as MavenProjectId)
            createCommandsForDependents(paramObject, dependent)
        }
        return paramObject
    }

    private fun createCommandsForDependents(paramObject: ParamObject, dependency: Project) {
        paramObject.initDependency(dependency)

        paramObject.analyser.getDependentsOf(paramObject.dependencyId).forEach { relation ->
            paramObject.relation = relation
            if (paramObject.isDependencyNotSubmoduleOfRelation()) {
                val existingDependent = paramObject.projects[relation.id]
                when {
                    existingDependent == null ->
                        initDependent(paramObject)

                    existingDependent.level < paramObject.getAnticipatedLevel() ->
                        checkForCyclicAndUpdateIfOk(paramObject, existingDependent)

                    else ->
                        //TODO rethink this branch, couldn't we miss a cyclic dependency?
                        updateCommandsAddDependentAddToProjectsAndUpdateMultiModuleIfNecessary(paramObject, existingDependent)
                }
            }
        }
    }

    private fun initDependent(paramObject: ParamObject) {
        val newDependent = createInitialWaitingProject(paramObject)
        paramObject.dependents[newDependent.id] = hashSetOf()
        paramObject.levelIterator.addToNextLevel(newDependent.id to newDependent)
        updateCommandsAddDependentAddToProjectsAndUpdateMultiModuleIfNecessary(paramObject, newDependent)
    }

    private fun checkForCyclicAndUpdateIfOk(paramObject: ParamObject, existingDependent: Project) : Boolean {
        analyseCycles(paramObject, existingDependent)
        return if (paramObject.hasRelationNoCycleToDependency()) {
            //TODO if we have submodule with a dependent and we need to update the level of the multi module
            //then we need to update the level even in case of a cycle -> write spec
            val dependent = updateLevelIfNecessaryAndRevisitInCase(paramObject, existingDependent)
            updateCommandsAddDependentAddToProjectsAndUpdateMultiModuleIfNecessary(paramObject, dependent)
            true
        } else {
            //we ignore the relation because it would introduce a cyclic dependency which we currently do not support.
            false
        }
    }

    private fun updateLevelIfNecessaryAndRevisitInCase(paramObject: ParamObject, existingDependent: Project): Project {
        val level = determineLevel(paramObject)
        if (existingDependent.level != level) {
            val updatedDependent = Project(existingDependent, level)
            paramObject.projects[existingDependent.id] = updatedDependent
            //we need to re-visit at least this project so that we can update the levels of the dependents as well
            paramObject.levelIterator.removeIfOnSameLevelAndReAddOnNext(updatedDependent.id to updatedDependent)
            return updatedDependent
        }
        return existingDependent
    }

    /**
     * It actually adds the given [dependent] to [ParamObject.projects] or *updates* the entry if already exists
     * => not only Add as indicated in the method name
     */
    private fun updateCommandsAddDependentAddToProjectsAndUpdateMultiModuleIfNecessary(
        paramObject: ParamObject,
        dependent: Project
    ) {
        addAndUpdateCommandsOfDependent(paramObject, dependent)
        addDependentToDependentsOfDependency(paramObject, dependent)
        paramObject.projects[dependent.id] = dependent
        updateMultiModuleIfNecessary(dependent, paramObject)
    }

    private fun updateMultiModuleIfNecessary(dependent: Project, paramObject: ParamObject) {
        if (dependent.isSubmodule) {
            val multiModules = paramObject.analyser.getMultiModules(paramObject.relation.id)
            if (multiModules.isNotEmpty()) {
                val topMultiModuleId = multiModules.last()
                val topMultiModule = paramObject.projects[topMultiModuleId]
                if (topMultiModule != null && topMultiModule.level < dependent.level) {
                    val tmpRelation = paramObject.relation
                    paramObject.relation = Relation(topMultiModuleId, topMultiModule.currentVersion, false)
                    val wouldHaveCycles = !checkForCyclicAndUpdateIfOk(paramObject, topMultiModule)
                    if (wouldHaveCycles) {
                        paramObject.initDependency(topMultiModule)
                        paramObject.relation = tmpRelation
                        //updating the multi module would introduce a cycle, thus we need to adjust the submodule instead
                        updateLevelIfNecessaryAndRevisitInCase(paramObject, dependent)
                    }
                }
            }
        }
    }

    private fun addAndUpdateCommandsOfDependent(paramObject: ParamObject, dependent: Project) {
        // if the version is not self managed then it suffices that we have a dependency to the project
        // which manages this version for us, we do not need to do anything here.
        if (paramObject.isRelationDependencyVersionNotSelfManaged()) return

        // we do not have to update the commands if only the level changed
        if (paramObject.isRelationAlreadyDependentOfDependency()) return

        val dependencyId = paramObject.dependencyId
        val list = dependent.commands as MutableList
        if (paramObject.isRelationNotSubmodule()) {
            addDependencyToReleaseCommands(list, dependencyId)
        }

        // submodule -> multi module relation is updated by M2 Release Plugin
        // if relation is a submodule and dependency as well and they share a common multi module, then we do not
        // need a to update anything because the submodules will have the same version after releasing
        //TODO not entirely true, we still need to update the version to the new one
        if (paramObject.isRelationNotInSameMultiModuleCircleAsDependency()) {
            val state = CommandState.Waiting(setOf(dependencyId))
            list.add(0, JenkinsUpdateDependency(state, dependencyId))
        }
    }

    private fun addDependencyToReleaseCommands(list: List<Command>, dependencyId: MavenProjectId) {
        list.filter { it is ReleaseCommand }.forEach { command ->
            val state = command.state
            if (state is CommandState.Waiting) {
                (state.dependencies as MutableSet).add(dependencyId)
            } else if (state !== CommandState.Disabled) {
                throw IllegalStateException("only state Waiting and Disabled expected, found: $state")
            }
        }
    }

    private fun addDependentToDependentsOfDependency(paramObject: ParamObject, dependent: Project) {
        paramObject.getDependentsOfDependency().add(dependent.id)
    }

    private fun createInitialWaitingProject(paramObject: ParamObject): Project {
        val relation = paramObject.relation
        val isNotSubmodule = paramObject.isRelationNotSubmodule()
        val commands = if (isNotSubmodule) {
            mutableListOf(
                createJenkinsReleasePlugin(
                    paramObject.analyser,
                    relation.id,
                    relation.currentVersion,
                    CommandState.Waiting(mutableSetOf(paramObject.dependencyId))
                )
            )
        } else {
            mutableListOf()
        }
        val level = determineLevel(paramObject)
        return createInitialProject(relation.id, !isNotSubmodule, relation.currentVersion, level, commands)
    }

    private fun determineLevel(paramObject: ParamObject): Int {
        return if (paramObject.isRelationNotInSameMultiModuleCircleAsDependency()) {
            paramObject.getAnticipatedLevel()
        } else {
            paramObject.dependency.level
        }
    }

    private fun analyseCycles(paramObject: ParamObject, existingDependent: Project) {
        val dependencyId = paramObject.dependencyId
        val visitedProjects = hashSetOf<ProjectId>()
        val projectsToVisit = linkedMapOf(existingDependent.id to mutableListOf<ProjectId>(dependencyId))
        while (projectsToVisit.isNotEmpty()) {
            val (dependentId, dependentBranch) = projectsToVisit.iterator().next()
            projectsToVisit.remove(dependentId)
            visitedProjects.add(dependentId)
            paramObject.getDependent(dependentId).forEach {
                if (it == dependencyId) {
                    if (paramObject.isRelationNotInSameMultiModuleCircleAsDependency()) {
                        val map = paramObject.cyclicDependents.getOrPut(existingDependent.id, { linkedMapOf() })
                        map[dependencyId] = dependentBranch
                        return
                    } else {
                        val map = paramObject.interModuleCyclicDependents.getOrPut(existingDependent.id, { linkedMapOf() })
                        map[dependencyId] = dependentBranch
                        //we cannot stop here because cyclic inter module dependencies can be dealt with others not (yet)
                    }
                } else if (!visitedProjects.contains(it)) {
                    projectsToVisit[it] = ArrayList<ProjectId>(dependentBranch.size + 1).apply {
                        addAll(dependentBranch)
                        add(paramObject.getProject(it).id)
                    }
                }
            }
        }
    }

    private fun reportCyclicDependencies(paramObject: ParamObject, warnings: MutableList<String>) {
        paramObject.cyclicDependents.mapTo(warnings, { (projectId, dependentEntry) ->
            val sb = StringBuilder()
            sb.append("Project ").append(projectId.identifier).append(" has one or more cyclic dependencies. ")
                .append("The corresponding relation (first ->) was ignored, you need to address this circumstance manually:\n")
            appendCyclicDependents(sb, projectId, dependentEntry.values)
            sb.toString()
        })
    }

    private fun reportInterModuleCyclicDependencies(paramObject: ParamObject, infos: MutableList<String>) {
        paramObject.interModuleCyclicDependents.mapTo(infos, { (projectId, dependentEntry) ->
            val sb = StringBuilder()
            sb.append("Project ").append(projectId.identifier).append(" has one or more inter module cyclic dependencies. ")
                .append("Will be handled by the M2 Release Plugin but you should reconsider your design:\n")
            appendCyclicDependents(sb, projectId, dependentEntry.values)
            sb.toString()
        })
    }

    private fun appendCyclicDependents(
        sb: StringBuilder,
        dependency: ProjectId,
        dependencies: Collection<List<ProjectId>>
    ) {
        sb.append("-> ")
        dependencies.appendToStringBuilder(sb, "\n-> ") { list, sb1 ->
            list.appendToStringBuilder(sb1, " -> ") { it, sb2 ->
                sb2.append(it.identifier)
            }
            sb1.append(" -> ").append(dependency.identifier)
        }
    }

    private class ParamObject(
        val analyser: Analyser,
        rootProject: Project
    ){
        val projects =  hashMapOf(rootProject.id to rootProject)
        val submodules = hashMapOf<ProjectId, Set<ProjectId>>()
        val dependents = hashMapOf(rootProject.id to hashSetOf<ProjectId>())
        val cyclicDependents = hashMapOf<ProjectId, LinkedHashMap<ProjectId, MutableList<ProjectId>>>()
        val interModuleCyclicDependents = hashMapOf<ProjectId, LinkedHashMap<ProjectId, MutableList<ProjectId>>>()
        val levelIterator = LevelIterator(rootProject.id to rootProject)

        private lateinit var _dependency: Project
        var dependency: Project
            get() = _dependency
            private set(value) {
                _dependency = value
            }
        private lateinit var _dependencyId: MavenProjectId
        var dependencyId: MavenProjectId
            get() = _dependencyId
            private set(value) {
                _dependencyId = value
            }
        lateinit var relation: Relation<MavenProjectId>

        fun initDependency(dependency: Project) {
            this.dependency = dependency
            this.dependencyId = dependency.id as MavenProjectId
        }

        /**
         * Returns [dependency.level][Project.level] + 1 which is only the anticipated level because it depends on
         * whether [relation] is a submodule of [dependency] etc.
         */
        fun getAnticipatedLevel() = dependency.level + 1

        /**
         * Returns true if the [relation] is not a (nested) submodule of [dependencyId] and if they are not a submodule
         * of a same common (super) multi module.
         *
         * Or in other words returns `true` if they are not in the same multi module circle; otherwise false.
         */
        fun isRelationNotInSameMultiModuleCircleAsDependency(): Boolean {
            return isRelationNotSubmoduleOfDependency() &&
                isDependencyNotSubmoduleOfRelation() &&
                relationAndDependencyHaveNotCommonMultiModule()
        }

        fun isRelationNotSubmodule() = !analyser.isSubmodule(relation.id)
        fun isRelationNotSubmoduleOfDependency() = !analyser.isSubmoduleOf(relation.id, dependencyId)
        fun isDependencyNotSubmoduleOfRelation() = !analyser.isSubmoduleOf(dependencyId, relation.id)

        private fun relationAndDependencyHaveNotCommonMultiModule(): Boolean {
            val multiModulesOfDependency = analyser.getMultiModules(dependencyId)
            return multiModulesOfDependency.isEmpty() ||
                !analyser.getMultiModules(relation.id).asSequence().any {
                    multiModulesOfDependency.contains(it)
                }
        }

        fun isRelationDependencyVersionNotSelfManaged() = !relation.isDependencyVersionSelfManaged
        fun isRelationAlreadyDependentOfDependency() = getDependentsOfDependency().contains(relation.id)
        fun hasRelationNoCycleToDependency(): Boolean {
            return when {
                cyclicDependents[relation.id]?.containsKey(dependencyId) == true -> false
                interModuleCyclicDependents[relation.id]?.containsKey(dependencyId) == true -> false
                else -> true
            }
        }

        fun getProject(projectId: ProjectId)
            = projects[projectId] ?: throw IllegalStateException("$projectId was not found in projects")

        fun getDependent(projectId: ProjectId)
            = dependents[projectId] ?: throw IllegalStateException("$projectId was not found in dependents")

        fun getDependentsOfDependency() = getDependent(dependencyId)
    }


    /**
     * Options for the [JenkinsReleasePlanCreator].
     */
    data class Options(
        val disableReleaseFor: Regex
    )
}
