package ch.loewenfels.depgraph.data

/**
 * Represents a release plan where the [Project] with the given [rootProjectId] shall be released.
 *
 * Dependent projects (incl. transitive) of the root project as well as the root project itself
 * are stored in [projects]. The relation between the projects are stored in [dependents]
 * where the key represents the dependency and the value the dependent.
 */
data class ReleasePlan(
    val rootProjectId: ProjectId,
    val projects: Map<ProjectId, Project>,
    val dependents: Map<ProjectId, Set<ProjectId>>
) {
    constructor(releasePlan: ReleasePlan, projects: Map<ProjectId, Project>) :
        this(releasePlan.rootProjectId, projects, releasePlan.dependents)

    fun getProject(projectId: ProjectId): Project {
        return projects[projectId] ?: throw IllegalArgumentException("Could not find the project with id $projectId")
    }

    fun getDependents(projectId: ProjectId): Set<ProjectId> {
        return dependents[projectId]
            ?: throw IllegalArgumentException("Could not find dependents for project with id $projectId")
    }

    fun iterator(): Iterator<Project> = ReleasePlanIterator(this)

    private class ReleasePlanIterator(private val releasePlan: ReleasePlan) : Iterator<Project> {
        private val projectsToVisit = mutableListOf(releasePlan.rootProjectId)
        private val visitedProjects = hashSetOf<ProjectId>()

        override fun hasNext() = projectsToVisit.isNotEmpty()
        override fun next(): Project {
            val projectId = projectsToVisit.removeAt(0)
            if (!visitedProjects.contains(projectId)) {
                visitedProjects.add(projectId)
                projectsToVisit.addAll(releasePlan.getDependents(projectId).filter { !visitedProjects.contains(it) })
                return releasePlan.getProject(projectId)
            } else {
                throw NoSuchElementException("No project left; rootProjectId was ${releasePlan.rootProjectId}")
            }
        }
    }
}
