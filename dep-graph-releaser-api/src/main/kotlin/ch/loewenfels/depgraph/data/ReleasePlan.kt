package ch.loewenfels.depgraph.data

/**
 * Represents a release plan whereas the first [Project] in [projects] is the root project,
 * the one which should be released, whereas the others are dependent projects.
 */
data class ReleasePlan(val projects: List<Project>)
