package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.Project

interface JobExecutionDataFactory {

    fun create(project: Project, command: Command): JobExecutionData
}
