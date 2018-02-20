package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.tutteli.atrium.api.cc.en_UK.and
import ch.tutteli.atrium.api.cc.en_UK.property
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.creating.Assert

data class IdAndVersions(val id: ProjectId, val newVersion: String, val nextDevVersion: String)

fun Assert<Project>.idAndNewVersion(idAndVersions: IdAndVersions) = and {
    property(subject::id).toBe(idAndVersions.id)
    property(subject::newVersion).toBe(idAndVersions.newVersion)
}
