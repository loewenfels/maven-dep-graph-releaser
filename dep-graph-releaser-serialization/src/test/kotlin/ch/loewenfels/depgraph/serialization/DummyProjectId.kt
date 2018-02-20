package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.ProjectId

data class DummyProjectId(override val identifier: String) : ProjectId
