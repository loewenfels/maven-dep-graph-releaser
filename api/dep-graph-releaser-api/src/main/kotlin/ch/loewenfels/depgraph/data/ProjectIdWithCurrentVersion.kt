package ch.loewenfels.depgraph.data

data class ProjectIdWithCurrentVersion<out T : ProjectId>(val id: T, val currentVersion: String)
