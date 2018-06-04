package ch.loewenfels.depgraph.gui.components

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsCommand
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.gui.*
import ch.loewenfels.depgraph.gui.jobexecution.GITHUB_NEW_ISSUE
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.tutteli.kbox.toPeekingIterator
import kotlinx.html.*
import kotlinx.html.dom.append
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import kotlin.dom.addClass
import kotlin.dom.removeClass

class Pipeline(private val releasePlan: ReleasePlan, private val menu: Menu) {
    private val contextMenu = ContextMenu(releasePlan, menu)

    init {
        setUpProjects()
        Toggler(releasePlan, menu)
        contextMenu.setUpCommandsOnContextMenu()
    }

    private fun setUpProjects() {
        val set = hashSetOf<ProjectId>()
        val pipeline = elementById(PIPELINE_HTML_ID)
        pipeline.asDynamic().state = releasePlan.state
        pipeline.append {
            val itr = releasePlan.iterator().toPeekingIterator()
            var level: Int
            while (itr.hasNext()) {
                val project = itr.next()
                level = project.level

                div("level l$level") {
                    if (!project.isSubmodule) {
                        project(project)
                    }
                    set.add(project.id)
                    while (itr.hasNextOnTheSameLevel(level)) {
                        val nextProject = itr.next()
                        if (!nextProject.isSubmodule) {
                            project(nextProject)
                        }
                        set.add(nextProject.id)
                    }
                }
            }
        }
        val involvedProjects = set.size
        showStatus("Projects involved: $involvedProjects")
        if (involvedProjects != releasePlan.getNumberOfProjects()) {
            showError("""
                |Not all dependent projects are involved in the process.
                |Please report a bug: $GITHUB_NEW_ISSUE
                |The following projects where left out of the analysis:
                |${(releasePlan.getProjectIds() - set).joinToString("\n") { it.identifier }}
            """.trimMargin())
        }
    }

    private fun DIV.project(project: Project) {
        div {
            val hasCommands = project.commands.isNotEmpty()
            classes = setOf(
                "project",
                if (project.isSubmodule) "submodule" else "",
                if (!hasCommands) "withoutCommands" else "",
                if (releasePlan.hasSubmodules(project.id)) "withSubmodules" else ""
            )

            val identifier = project.id.identifier
            this.id = identifier
            div("title") {
                span {
                    projectId(project.id)
                }
            }
            if (!project.isSubmodule) {
                div("fields") {
                    textFieldReadOnlyWithLabel(
                        "$identifier:currentVersion", "Current Version", project.currentVersion, menu
                    )
                    textFieldWithLabel("$identifier:releaseVersion", "Release Version", project.releaseVersion, menu)
                }
            }
            commands(project)

            if (project.isSubmodule) {
                // means we are within a multi-module and might want to show submodules of this submodule
                submodules(project.id)
            }
        }
    }

    private fun CommonAttributeGroupFacade.projectId(id: ProjectId) {
        if (id is MavenProjectId) {
            title = id.identifier
            +id.artifactId
        } else {
            +id.identifier
        }
    }

    private fun INPUT.projectId(id: ProjectId) {
        if (id is MavenProjectId) {
            title = id.identifier
            value = id.artifactId
        } else {
            value = id.identifier
        }
    }

    private fun DIV.commands(project: Project) {
        project.commands.forEachIndexed { index, command ->
            div {
                val commandId = getCommandId(project, index)
                id = commandId
                classes = setOf("command", stateToCssClass(command.state))
                div("commandTitle") {
                    id = "$commandId$TITLE_SUFFIX"
                    +command::class.simpleName!!
                }
                div("fields") {
                    fieldsForCommand(commandId, project, index, command)
                }
                val div = getUnderlyingHtmlElement().asDynamic()
                div.state = command.state
                if (command is JenkinsCommand) {
                    div.buildUrl = command.buildUrl
                }
            }
        }
    }

    private fun DIV.fieldsForCommand(idPrefix: String, project: Project, index: Int, command: Command) {
        val cssClass = if (command is ReleaseCommand) "release" else ""
        val isNotDeactivated = command.state !is CommandState.Deactivated

        toggle(
            "$idPrefix$DEACTIVATE_SUFFIX",
            if (isNotDeactivated) "Click to deactivate command" else "Click to activate command",
            isNotDeactivated,
            command.state === CommandState.Disabled,
            cssClass
        )
        a(classes = "state") {
            id = "$idPrefix$STATE_SUFFIX"
            i("material-icons") {
                span()
                id = "$idPrefix:status.icon"
            }
            if (command is JenkinsCommand) {
                href = command.buildUrl ?: ""
            }
            title = stateToTitle(command.state)
        }
        this@Pipeline.contextMenu.createContextMenu(this, idPrefix, project, index)

        when (command) {
            is JenkinsMavenReleasePlugin ->
                appendJenkinsMavenReleasePluginField(idPrefix, command)
            is JenkinsMultiMavenReleasePlugin ->
                appendJenkinsMultiMavenReleasePluginFields(idPrefix, project.id, command)
            is JenkinsUpdateDependency ->
                appendJenkinsUpdateDependencyField(idPrefix, command)
            else ->
                showError("Unknown command found, cannot display its fields.\n$command")
        }
    }

    private fun DIV.appendJenkinsMavenReleasePluginField(idPrefix: String, command: JenkinsMavenReleasePlugin) {
        fieldNextDevVersion(idPrefix, command, command.nextDevVersion)
    }

    private fun DIV.fieldNextDevVersion(idPrefix: String, command: Command, nextDevVersion: String) {
        textFieldWithLabel("$idPrefix$NEXT_DEV_VERSION_SUFFIX", "Next Dev Version", nextDevVersion, menu) {
            if (command.state === CommandState.Disabled) {
                disabled = true
            }
        }
    }

    private fun DIV.appendJenkinsMultiMavenReleasePluginFields(
        idPrefix: String,
        projectId: ProjectId,
        command: JenkinsMultiMavenReleasePlugin
    ) {
        fieldNextDevVersion(idPrefix, command, command.nextDevVersion)
        submodules(projectId)
    }

    private fun DIV.submodules(projectId: ProjectId) {
        val submodules = releasePlan.getSubmodules(projectId)
        if (submodules.isEmpty()) return

        div("submodules") {
            submodules.forEach {
                project(releasePlan.getProject(it))
            }
        }
    }

    private fun DIV.appendJenkinsUpdateDependencyField(idPrefix: String, command: JenkinsUpdateDependency) {
        textFieldReadOnlyWithLabel("$idPrefix:groupId", "Dependency", command.projectId.identifier, menu) {
            projectId(command.projectId)
        }
    }

    private fun DIV.toggle(
        idCheckbox: String,
        title: String,
        checked: Boolean,
        disabled: Boolean,
        checkboxCssClass: String = ""
    ) {
        label("toggle") {
            checkBoxInput(classes = checkboxCssClass) {
                this.id = idCheckbox
                this.checked = checked && !disabled
                this.disabled = disabled
            }
            span("slider") {
                this.id = "$idCheckbox$SLIDER_SUFFIX"
                this.title = title
                if (disabled) {
                    this.title = STATE_DISABLED
                }
            }
        }
    }

    companion object {
        private const val PIPELINE_HTML_ID = "pipeline"

        private const val STATE_WAITING = "Wait for dependent projects to complete."
        const val STATE_READY = "Ready to be queued for execution."
        const val STATE_READY_TO_BE_TRIGGER = "Ready to be re-scheduled"
        const val STATE_QUEUEING = "Currently queueing the job."
        const val STATE_IN_PROGRESS = "Job is running."
        const val STATE_SUCCEEDED = "Job completed successfully."
        const val STATE_FAILED = "Job failed - click to navigate to console."
        private const val STATE_DEACTIVATED = "Currently deactivated, click to activate"
        const val STATE_DISABLED = "Command disabled, cannot be reactivated."

        const val DEACTIVATE_SUFFIX = ":deactivate"
        const val SLIDER_SUFFIX = ":slider"
        const val NEXT_DEV_VERSION_SUFFIX = ":nextDevVersion"
        const val STATE_SUFFIX = ":state"
        const val TITLE_SUFFIX = ":title"


        fun getCommandId(project: Project, index: Int) = getCommandId(project.id, index)
        fun getCommandId(projectId: ProjectId, index: Int) = "${projectId.identifier}:$index"
        fun getCommand(project: Project, index: Int) = getCommand(project.id, index)
        fun getCommand(projectId: ProjectId, index: Int): HTMLElement = elementById(getCommandId(projectId, index))

        fun getToggle(project: Project, index: Int) =
            getCheckbox("${getCommandId(project.id, index)}$DEACTIVATE_SUFFIX")

        fun getCommandState(projectId: ProjectId, index: Int) = getCommandState(getCommandId(projectId, index))
        fun getCommandState(idPrefix: String) = elementById(idPrefix).asDynamic().state as CommandState

        fun changeStateOfCommandAndAddBuildUrl(
            project: Project,
            index: Int,
            newState: CommandState,
            title: String,
            buildUrl: String
        ) {
            changeStateOfCommand(project, index, newState, title)
            val commandId = getCommandId(project, index)
            elementById<HTMLAnchorElement>("$commandId$STATE_SUFFIX").href = buildUrl
            elementById(commandId).asDynamic().buildUrl = buildUrl
        }

        fun changeStateOfCommand(project: Project, index: Int, newState: CommandState, title: String) {
            changeStateOfCommand(project, index, newState, title) { previousState, commandId ->
                try {
                    previousState.checkTransitionAllowed(newState)
                } catch (e: IllegalStateException) {
                    val commandTitle =
                        elementById(commandId + TITLE_SUFFIX)
                    throw IllegalStateException(
                        "Cannot change the state of the command ${commandTitle.innerText} (${index + 1}. command) " +
                            "of the project ${project.id.identifier}",
                        e
                    )
                }
            }
        }

        internal fun changeStateOfCommand(
            project: Project,
            index: Int, newState:
            CommandState, title: String,
            checkStateTransition: (previousState: CommandState, commandId: String) -> CommandState
        ) {
            val commandId = getCommandId(project, index)
            val command = elementById(commandId)
            val dynCommand = command.asDynamic()
            val previousState = dynCommand.state as CommandState
            dynCommand.state = checkStateTransition(previousState, commandId)
            command.removeClass(stateToCssClass(previousState))
            command.addClass(stateToCssClass(newState))
            elementById("$commandId$STATE_SUFFIX")
                .title = title
        }

        fun getReleaseState() = elementById(PIPELINE_HTML_ID).asDynamic().state as ReleaseState

        fun changeReleaseState(newState: ReleaseState) {
            val pipeline = elementById(PIPELINE_HTML_ID).asDynamic()
            pipeline.state = getReleaseState().checkTransitionAllowed(newState)
        }

        private fun stateToCssClass(state: CommandState) = when (state) {
            is CommandState.Waiting -> "waiting"
            CommandState.Ready -> "ready"
            CommandState.ReadyToReTrigger -> "readyToReTrigger"
            CommandState.Queueing -> "queueing"
            CommandState.InProgress -> "inProgress"
            CommandState.Succeeded -> "succeeded"
            is CommandState.Failed -> "failed"
            is CommandState.Deactivated -> "deactivated"
            CommandState.Disabled -> "disabled"
        }

        internal fun stateToTitle(state: CommandState) = when (state) {
            is CommandState.Waiting -> STATE_WAITING
            CommandState.Ready -> STATE_READY
            CommandState.ReadyToReTrigger -> STATE_READY_TO_BE_TRIGGER
            CommandState.Queueing -> STATE_QUEUEING
            CommandState.InProgress -> STATE_IN_PROGRESS
            CommandState.Succeeded -> STATE_SUCCEEDED
            CommandState.Failed -> STATE_FAILED
            is CommandState.Deactivated -> STATE_DEACTIVATED
            CommandState.Disabled -> STATE_DISABLED
        }
    }
}
