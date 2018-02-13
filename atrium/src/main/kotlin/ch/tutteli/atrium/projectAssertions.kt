package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.tutteli.atrium.api.cc.en_UK.and
import ch.tutteli.atrium.api.cc.en_UK.property
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.creating.Assert

fun Assert<Project>.idAndNewVersion(projectId: ProjectId, newVersion: String) = and {
    property(subject::id).toBe(projectId)
    property(subject::newVersion).toBe(newVersion)
}
