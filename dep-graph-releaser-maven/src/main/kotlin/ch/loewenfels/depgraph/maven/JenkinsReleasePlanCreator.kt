package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.kbox.appendToStringBuilder

class JenkinsReleasePlanCreator(private val versionDeterminer: VersionDeterminer) {

    fun create(projectToRelease: MavenProjectId, analyser: Analyser): ReleasePlan {
        val currentVersion = analyser.getCurrentVersion(projectToRelease)
        require(currentVersion != null) {
            """Can only release a project which is part of the analysis.
                |Given: $projectToRelease
                |Analysed projects: ${analyser.getAnalysedProjectsAsString()}
            """.trimMargin()
        }

        require(!analyser.isSubmodule(projectToRelease)) {
            """Cannot release a submodule, the given project is part of a multi module hierarchy
                |Given: $projectToRelease
                |Multi modules: ${analyser.getMultiModules(projectToRelease).joinToString(",")}
            """.trimMargin()
        }

        val rootProject = createRootProject(analyser, projectToRelease, currentVersion)
        val paramObject = createDependents(analyser, rootProject)

        val warnings = mutableListOf<String>()
        turnCyclicDependenciesIntoWarnings(paramObject, warnings)
        warnings.addAll(analyser.getErroneousPomFiles())

        return ReleasePlan(rootProject.id, paramObject.projects, paramObject.dependents, warnings)
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
        val nextDevVersion = versionDeterminer.nextDevVersion(currentVersion)
        val submodules = analyser.getSubmodulesInclNested(projectId)
        val isNotMultiModule = submodules.isEmpty()
        return if (isNotMultiModule) {
            JenkinsMavenReleasePlugin(state, nextDevVersion)
        } else {
            JenkinsMultiMavenReleasePlugin(state, nextDevVersion, submodules)
        }
    }

    private fun createDependents(analyser: Analyser, rootProject: Project): ParamObject {
        val paramObject = ParamObject(
            analyser,
            projects = hashMapOf(rootProject.id to rootProject),
            dependents = hashMapOf(rootProject.id to hashSetOf()),
            cyclicDependents = hashMapOf(),
            level = 1,
            dependentsToVisit = mutableListOf(linkedMapOf(rootProject.id to rootProject))
        )
        val dependentsToVisit = paramObject.dependentsToVisit
        while (dependentsToVisit.isNotEmpty()) {
            val dependentsOnSameLevel = dependentsToVisit[0]
            val dependent = dependentsOnSameLevel.remove(dependentsOnSameLevel.iterator().next().key)!!
            createCommandsForDependents(paramObject, dependent)
            if (dependentsOnSameLevel.isEmpty()) {
                dependentsToVisit.removeAt(0)
                ++paramObject.level
            }
        }
        return paramObject
    }

    private fun createCommandsForDependents(paramObject: ParamObject, dependency: Project) {
        paramObject.dependencyId = dependency.id as MavenProjectId

        paramObject.analyser.getDependentsOf(paramObject.dependencyId).forEach { relation ->
            paramObject.relation = relation
            if (paramObject.isDependencyNotSubmoduleOfRelation()) {
                val existingDependent = paramObject.projects[relation.id]
                when {
                    existingDependent == null ->
                        initDependent(paramObject)

                    existingDependent.level < paramObject.level ->
                        checkForCyclicAndUpdateIfOk(paramObject, existingDependent)

                    else ->
                        updateCommandsAddDependentAndAddToProjects(paramObject, existingDependent)
                }
            }
        }
    }

    private fun initDependent(paramObject: ParamObject) {
        val newDependent = createInitialWaitingProject(paramObject)
        paramObject.dependents[newDependent.id] = hashSetOf()
        addToNextLevelOfDependentsToVisit(paramObject.dependentsToVisit, newDependent)
        updateCommandsAddDependentAndAddToProjects(paramObject, newDependent)
    }

    private fun checkForCyclicAndUpdateIfOk(paramObject: ParamObject, existingDependent: Project) {
        // TODO we should not analyse cycles if the two projects are in the same multi-module hierarchy
        // However, maybe the user wants to know that an inter module cyclic dependency exists -> introduce info?
        analyseCycles(paramObject, existingDependent)
        val cycles = paramObject.cyclicDependents[existingDependent.id]
        if (cycles == null || !cycles.containsKey(paramObject.dependencyId)) {
            val updatedDependent = Project(existingDependent, paramObject.level)
            paramObject.projects[existingDependent.id] = updatedDependent
            //we need to re-visit so that we can update the levels of the dependents as well
            removeIfVisitOnSameLevelAndReAddOnNext(updatedDependent, paramObject.dependentsToVisit)
            updateCommandsAddDependentAndAddToProjects(paramObject, updatedDependent)
        } else {
            //we ignore the relation because it would introduce a cyclic dependency which we currently do not support.
        }
    }

    /**
     * It actually adds the given [dependent] to [ParamObject.projects] or updates the entry if already exists.
     */
    private fun updateCommandsAddDependentAndAddToProjects(paramObject: ParamObject, dependent: Project) {
        addAndUpdateCommandsOfDependent(paramObject, dependent)
        addDependentToDependentsOfDependency(paramObject, dependent)
        paramObject.projects[dependent.id] = dependent
    }

    private fun addAndUpdateCommandsOfDependent(paramObject: ParamObject, dependent: Project) {
        // if the version is not self managed then it suffices that we have a dependency to the project
        // which manages this version for us, we do not need to do anything here.
        if (paramObject.isRelationDependencyVersionNotSelfManaged()) return

        // we do not have to update the commands if only the level changed
        if(paramObject.isRelationAlreadyDependentOfDependency()) return

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
            ((command.state as CommandState.Waiting).dependencies as MutableSet).add(dependencyId)
        }
    }

    private fun addDependentToDependentsOfDependency(paramObject: ParamObject, dependent: Project) {
        paramObject.dependents[paramObject.dependencyId]!!.add(dependent.id)
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
        return createInitialProject(relation.id, !isNotSubmodule, relation.currentVersion, paramObject.level, commands)
    }

    private fun analyseCycles(paramObject: ParamObject, existingDependent: Project) {
        val dependencyId = paramObject.dependencyId
        val visitedProjects = hashSetOf<ProjectId>()
        val projectsToVisit = linkedMapOf(existingDependent.id to mutableListOf<ProjectId>(dependencyId))
        while (projectsToVisit.isNotEmpty()) {
            val (dependentId, dependentBranch) = projectsToVisit.iterator().next()
            projectsToVisit.remove(dependentId)
            visitedProjects.add(dependentId)
            paramObject.dependents[dependentId]!!.forEach {
                if (it == dependencyId) {
                    val map = paramObject.cyclicDependents.getOrPut(existingDependent.id, { linkedMapOf() })
                    map[dependencyId] = dependentBranch
                    return
                } else if (!visitedProjects.contains(it)) {
                    projectsToVisit[it] = ArrayList<ProjectId>(dependentBranch.size + 1).apply {
                        addAll(dependentBranch)
                        add(paramObject.projects[it]!!.id)
                    }
                }
            }
        }
    }

    private fun removeIfVisitOnSameLevelAndReAddOnNext(
        updatedProject: Project,
        dependentsToVisit: MutableList<LinkedHashMap<ProjectId, Project>>
    ) {
        val dependentsOnSameLevel = dependentsToVisit[0]
        dependentsOnSameLevel.remove(updatedProject.id)
        addToNextLevelOfDependentsToVisit(dependentsToVisit, updatedProject)
    }

    private fun addToNextLevelOfDependentsToVisit(
        dependentsToVisit: MutableList<LinkedHashMap<ProjectId, Project>>,
        dependent: Project
    ) {
        if (dependentsToVisit.size <= 1) {
            dependentsToVisit.add(linkedMapOf())
        }
        val nextLevelProjects = dependentsToVisit.last()
        nextLevelProjects[dependent.id] = dependent
    }

    private fun turnCyclicDependenciesIntoWarnings(paramObject: ParamObject, warnings: MutableList<String>) {
        paramObject.cyclicDependents.mapTo(warnings, { (projectId, dependentEntry) ->
            val sb = StringBuilder()
            sb.append("Project ").append(projectId.identifier).append(" has one or more cyclic dependencies.")
                .append("The corresponding relation (first ->) was ignored, you need to address this circumstance manually:\n")
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
        val projects: HashMap<ProjectId, Project>,
        val dependents: HashMap<ProjectId, HashSet<ProjectId>>,
        val cyclicDependents: HashMap<ProjectId, LinkedHashMap<ProjectId, MutableList<ProjectId>>>,
        var level: Int,
        val dependentsToVisit: MutableList<LinkedHashMap<ProjectId, Project>>
    ) {
        lateinit var dependencyId: MavenProjectId
        lateinit var relation: Relation<MavenProjectId>

        fun isRelationNotSubmodule() = !analyser.isSubmodule(relation.id)

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


        private fun isRelationNotSubmoduleOfDependency() = !analyser.isSubmoduleOf(relation.id, dependencyId)
        fun isDependencyNotSubmoduleOfRelation() = !analyser.isSubmoduleOf(dependencyId, relation.id)

        private fun relationAndDependencyHaveNotCommonMultiModule(): Boolean {
            val multiModulesOfDependency = analyser.getMultiModules(dependencyId)
            return multiModulesOfDependency.isEmpty() ||
                !analyser.getMultiModules(relation.id).asSequence().any {
                    multiModulesOfDependency.contains(it)
                }
        }

        fun isRelationDependencyVersionNotSelfManaged() = !relation.isDependencyVersionSelfManaged
        fun isRelationAlreadyDependentOfDependency() = dependents[dependencyId]!!.contains(relation.id)
    }
}
