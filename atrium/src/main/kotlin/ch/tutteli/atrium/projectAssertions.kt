package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.ProjectId

data class IdAndVersions(val id: ProjectId, val currentVersion: String, val releaseVersion: String, val nextDevVersion: String)
