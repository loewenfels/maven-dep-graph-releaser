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
        paramObject.dependencyId = dependency.id as MavenProjectId

        paramObject.analyser.getDependentsOf(paramObject.dependencyId).forEach { dependentIdWithVersion ->
            paramObject.relation = dependentIdWithVersion
            val existingDependent = paramObject.projects[dependentIdWithVersion.id]
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

    private fun initDependent(paramObject: ParamObject): Project {
        val newDependent =
            createInitialWaitingProject(paramObject.relation, paramObject.level, paramObject.dependencyId)
        paramObject.dependents[newDependent.id] = hashSetOf()
        addToNextLevelOfDependentsToVisit(paramObject.dependentsToVisit, newDependent)
        updateCommandsAddDependentAndAddToProjects(paramObject, newDependent)
        return newDependent
    }

    private fun checkForCyclicAndUpdateIfOk(paramObject: ParamObject, existingDependent: Project): Project {
        analyseCycles(paramObject, existingDependent)
        val cycles = paramObject.cyclicDependents[existingDependent.id]
        return if (cycles == null || !cycles.containsKey(paramObject.dependencyId)) {
            val updatedDependent = Project(existingDependent, paramObject.level)
            paramObject.projects[existingDependent.id] = updatedDependent
            //we need to re-visit so that we can update the levels of the dependents as well
            removeIfVisitOnSameLevelAndReAddOnNext(updatedDependent, paramObject.dependentsToVisit)
            updateCommandsAddDependentAndAddToProjects(paramObject, updatedDependent)
            updatedDependent
        } else {
            existingDependent
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
        val dependencyId = paramObject.dependencyId
        val list = dependent.commands as MutableList

        addDependencyToReleaseCommands(list, dependencyId)
        if (paramObject.relation.dependencyVersion != null) {
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

    private fun createInitialWaitingProject(
        relation: Relation<MavenProjectId>,
        level: Int,
        dependencyId: MavenProjectId
    ): Project = createInitialProject(
        relation.id,
        relation.currentVersion,
        level,
        CommandState.Waiting(mutableSetOf(dependencyId))
    )

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
    }
}
