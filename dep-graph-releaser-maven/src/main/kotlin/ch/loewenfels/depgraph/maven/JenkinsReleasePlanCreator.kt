package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
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

        val rootProject = createInitialProject(projectToRelease, currentVersion!!, 0, CommandState.Ready)
        val paramObject = createDependents(analyser, rootProject)

        val warnings = mutableListOf<String>()
        turnCyclicDependenciesIntoWarnings(paramObject, warnings)
        warnings.addAll(analyser.getErroneousPomFiles())

        return ReleasePlan(rootProject.id, paramObject.projects, paramObject.dependents, warnings)
    }

    private fun createInitialProject(
        projectId: MavenProjectId,
        currentVersion: String,
        level: Int,
        state: CommandState
    ) = Project(
        projectId,
        currentVersion,
        versionDeterminer.releaseVersion(currentVersion),
        level,
        mutableListOf(JenkinsMavenReleasePlugin(state, versionDeterminer.nextDevVersion(currentVersion)))
    )

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
        val dependencyId = dependency.id as MavenProjectId

        paramObject.analyser.getDependentsOf(dependencyId).forEach { dependentIdWithVersion ->

            val existingDependent = paramObject.projects[dependentIdWithVersion.id]
            when {
                existingDependent == null ->
                    initDependent(paramObject, dependentIdWithVersion, dependencyId)

                existingDependent.level < paramObject.level ->
                    checkForCyclicAndUpdateIfOk(paramObject, existingDependent, dependencyId)

                else ->
                    updateCommandsAddDependentAndAddToProjects(paramObject, existingDependent, dependencyId)
            }
        }
    }

    private fun initDependent(
        paramObject: ParamObject,
        dependentIdWithVersion: ProjectIdWithCurrentVersion<MavenProjectId>,
        dependencyId: MavenProjectId
    ): Project {
        val newDependent = createInitialWaitingProject(dependentIdWithVersion, paramObject.level, dependencyId)
        paramObject.dependents[newDependent.id] = hashSetOf()
        addToNextLevelOfDependentsToVisit(paramObject.dependentsToVisit, newDependent)
        updateCommandsAddDependentAndAddToProjects(paramObject, newDependent, dependencyId)
        return newDependent
    }

    private fun checkForCyclicAndUpdateIfOk(
        paramObject: ParamObject,
        existingDependent: Project,
        dependencyId: MavenProjectId
    ): Project {
        return if (checkIsNotCyclicOrTakeMeasures(paramObject, existingDependent, dependencyId)) {
            val updatedDependent = Project(existingDependent, paramObject.level)
            paramObject.projects[existingDependent.id] = updatedDependent
            //we need to re-visit so that we can update the levels of the dependents as well
            removeIfVisitOnSameLevelAndReAddOnNext(updatedDependent, paramObject.dependentsToVisit)
            updateCommandsAddDependentAndAddToProjects(paramObject, updatedDependent, dependencyId)
            updatedDependent
        } else {
            existingDependent
        }
    }

    /**
     * It actually adds the given [dependent] to [ParamObject.projects] or updates the entry if already exists.
     */
    private fun updateCommandsAddDependentAndAddToProjects(
        paramObject: ParamObject,
        dependent: Project,
        dependencyId: MavenProjectId
    ) {
        addAndUpdateCommandsOfDependent(dependent, dependencyId)
        addDependentToDependentsOfDependency(dependent.id, paramObject.dependents, dependencyId)
        paramObject.projects[dependent.id] = dependent
    }

    private fun addAndUpdateCommandsOfDependent(dependent: Project, dependencyId: MavenProjectId) {
        val list = dependent.commands as MutableList
        addDependencyToReleaseCommands(list, dependencyId)
        list.add(0, JenkinsUpdateDependency(CommandState.Waiting(setOf(dependencyId)), dependencyId))
    }

    private fun addDependencyToReleaseCommands(list: List<Command>, dependencyId: MavenProjectId) {
        list.filter { it is ReleaseCommand }.forEach { command ->
            ((command.state as CommandState.Waiting).dependencies as MutableSet).add(dependencyId)
        }
    }

    private fun addDependentToDependentsOfDependency(
        dependentId: ProjectId,
        dependents: HashMap<ProjectId, HashSet<ProjectId>>,
        dependencyId: MavenProjectId
    ) {
        dependents[dependencyId]!!.add(dependentId)
    }

    private fun createInitialWaitingProject(
        dependentIdWithVersion: ProjectIdWithCurrentVersion<MavenProjectId>,
        level: Int,
        dependencyId: MavenProjectId
    ): Project = createInitialProject(
        dependentIdWithVersion.id,
        dependentIdWithVersion.currentVersion,
        level,
        CommandState.Waiting(mutableSetOf(dependencyId))
    )

    private fun checkIsNotCyclicOrTakeMeasures(
        paramObject: ParamObject,
        existingDependent: Project,
        dependencyId: ProjectId
    ): Boolean {

        val visitedProjects = hashSetOf<ProjectId>()
        val projectsToVisit = linkedMapOf(existingDependent.id to mutableListOf(dependencyId))
        while (projectsToVisit.isNotEmpty()) {
            val (dependentId, dependentBranch) = projectsToVisit.iterator().next()
            projectsToVisit.remove(dependentId)
            visitedProjects.add(dependentId)
            paramObject.dependents[dependentId]!!.forEach {
                if (it == dependencyId) {
                    val set = paramObject.cyclicDependents.getOrPut(existingDependent.id, { mutableListOf() })
                    set.add(dependentBranch)
                    return false
                } else if (!visitedProjects.contains(it)) {
                    projectsToVisit[it] = ArrayList<ProjectId>(dependentBranch.size + 1).apply {
                        addAll(dependentBranch)
                        add(paramObject.projects[it]!!.id)
                    }
                }
            }
        }
        return true
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
        paramObject.cyclicDependents.mapTo(warnings, { (projectId, dependent) ->
            val sb = StringBuilder()
            sb.append("Project ").append(projectId.identifier).append(" has one or more cyclic dependencies:\n")
            appendCyclicDependents(sb, projectId, dependent)
            sb.toString()
        })
    }

    private fun appendCyclicDependents(
        sb: StringBuilder,
        dependency: ProjectId,
        dependencies: MutableList<MutableList<ProjectId>>
    ) {
        sb.append("-> ")
        dependencies.appendToStringBuilder(sb, "\n -> ") { list, sb1 ->
            list.appendToStringBuilder(sb1, " -> ") { it, sb2 ->
                sb2.append(it.identifier)
            }
        }
        sb.append(" -> ").append(dependency.identifier)
    }

    private class ParamObject(
        val analyser: Analyser,
        val projects: HashMap<ProjectId, Project>,
        val dependents: HashMap<ProjectId, HashSet<ProjectId>>,
        val cyclicDependents: HashMap<ProjectId, MutableList<MutableList<ProjectId>>>,
        var level: Int,
        val dependentsToVisit: MutableList<LinkedHashMap<ProjectId, Project>>
    )
}
