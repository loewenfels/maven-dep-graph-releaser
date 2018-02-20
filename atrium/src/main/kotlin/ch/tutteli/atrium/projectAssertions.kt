package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.tutteli.atrium.api.cc.en_UK.property
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.creating.Assert

data class IdAndVersions(val id: ProjectId, val currentVersion: String, val releaseVersion: String, val nextDevVersion: String)

fun Assert<Project>.idAndVersions(idAndVersions: IdAndVersions){
    property(subject::id).toBe(idAndVersions.id)
    property(subject::currentVersion).toBe(idAndVersions.currentVersion)
    property(subject::releaseVersion).toBe(idAndVersions.releaseVersion)
}
