package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsCommand
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.tutteli.kbox.forEachIn
import ch.tutteli.kbox.mapWithIndex
import ch.tutteli.kbox.toPeekingIterator
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onKeyUpFunction
import org.w3c.dom.*
import org.w3c.dom.events.MouseEvent
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.removeClass

class Gui(private val releasePlan: ReleasePlan, private val menu: Menu) {
    private val toggler = Toggler(releasePlan, menu)

    fun load() {
        val rootProjectId = releasePlan.rootProjectId
        val htmlTitle = (rootProjectId as? MavenProjectId)?.artifactId ?: rootProjectId.identifier
        document.title = "Release $htmlTitle"
        setUpMessages(releasePlan.warnings, "warnings", { showWarning(it) })
        setUpMessages(releasePlan.infos, "infos", { showInfo(it) })
        setUpConfig(releasePlan)
        setUpProjects()
        toggler.registerToggleEvents()
        setUpCommandsOnContextMenu()

        //TODO we should check if releasePlant.state is inProgress. In such a case it might be that command states
        // have changed already and we need to update the state let's say the browser crashes during release and we
        // have already triggered a job and know it is queued in this case we should check if it is no longer queued
        // but already started etc.
        //TODO also for state failed, might be that it failed because maxWaitingTime was over
    }

    private fun setUpMessages(messages: List<String>, id: String, action: (String) -> Unit) {
        if (messages.isNotEmpty()) {
            val minimized = elementById("${id}Minimized")
            minimized.style.display = "block"
            minimized.addEventListener("click", {
                minimized.style.display = "none"
                messages.forEach(action)
            })
        }
        val messagesDiv = elementById("messages")
        elementById(HIDE_MESSAGES_HTML_ID).addClickEventListener {
            document.querySelectorAll("#messages > div")
                .asList()
                .forEach { messagesDiv.removeChild(it) }
        }
    }

    private fun setUpConfig(releasePlan: ReleasePlan) {
        //TODO add description for each property
        elementById("config").append {
            div {
                textFieldWithLabel(RELEASE_ID_HTML_ID, "ReleaseId", releasePlan.releaseId)

                val config = releasePlan.config
                listOf(
                    ConfigKey.COMMIT_PREFIX,
                    ConfigKey.UPDATE_DEPENDENCY_JOB,
                    ConfigKey.REMOTE_REGEX,
                    ConfigKey.REMOTE_JOB,
                    ConfigKey.DRY_RUN_JOB,
                    ConfigKey.REGEX_PARAMS
                ).forEach { key ->
                    textFieldWithLabel("config-${key.asString()}", key.asString(), config[key] ?: "")
                }
                val key = ConfigKey.JOB_MAPPING
                textAreaWithLabel("config-${key.asString()}", key.asString(), config[key]?.replace("|", "\n") ?: "")
            }
        }
    }


    private fun setUpCommandsOnContextMenu() {
        val toggleLabels = document.querySelectorAll(".command > .fields > .toggle")
            .asList()
            .map { label ->
                val toggle = label.firstChild as HTMLInputElement
                label to toggle.id.substringBefore(DEACTIVATE_SUFFIX)
            }
        val stateIcons = document.querySelectorAll(".state")
            .asList()
            .map { aNode ->
                val a = aNode as HTMLAnchorElement
                a to a.id.substringBefore(STATE_SUFFIX)
            }
        forEachIn(toggleLabels, stateIcons) { (element, idPrefix) ->
            element.addEventListener("contextmenu", { event ->
                hideAllContextMenus()
                val contextMenu = elementById("$idPrefix$CONTEXT_MENU_SUFFIX")
                moveContextMenuPosition(event as MouseEvent, contextMenu)
                contextMenu.style.visibility = "visible"
                window.addEventListener("click", { hideAllContextMenus() }, js("({once: true})"))
                event.preventDefault()
                event.stopPropagation()
            })
        }
        window.addEventListener("contextmenu", { hideAllContextMenus() })
    }

    private fun hideAllContextMenus() {
        document.querySelectorAll(".contextMenu").asList().forEach {
            (it as HTMLElement).style.visibility = "hidden"
        }
    }

    private fun moveContextMenuPosition(event: MouseEvent, contextMenu: HTMLElement) {
        val menuWidth = contextMenu.offsetWidth
        val menuHeight = contextMenu.offsetHeight
        val mouseX = event.pageX
        val mouseY = event.pageY

        val x = if (mouseX + menuWidth > document.body!!.clientWidth + window.scrollX) {
            mouseX - menuWidth
        } else {
            mouseX
        }
        val y = if (mouseY + menuHeight > document.body!!.clientHeight + window.scrollY) {
            mouseY - menuHeight
        } else {
            mouseY
        }
        contextMenu.style.left = "${x}px"
        contextMenu.style.top = "${y}px"
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
            showError("Not all dependent projects are involved in the process, please report a bug. The following where left out\n" +
                (releasePlan.getProjectIds() - set).joinToString("\n") { it.identifier }
            )
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
                if (hasCommands) {
                    toggle(
                        "$identifier$DEACTIVATE_ALL_SUFFIX",
                        "deactivate all commands",
                        project.commands.any { it.state !is CommandState.Deactivated },
                        false
                    )
                }
                span {
                    projectId(project.id)
                }
            }
            if (!project.isSubmodule) {
                div("fields") {
                    textFieldReadOnlyWithLabel(
                        "$identifier:currentVersion",
                        "Current Version",
                        project.currentVersion
                    )
                    textFieldWithLabel("$identifier:releaseVersion", "Release Version", project.releaseVersion)
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

    private fun DIV.textFieldWithLabel(id: String, label: String, value: String) {
        textFieldWithLabel(id, label, value, {})
    }

    private fun DIV.textFieldReadOnlyWithLabel(
        id: String,
        label: String,
        value: String,
        inputAct: INPUT.() -> Unit = {}
    ) {
        textFieldWithLabel(id, label, value, { readonly = true; inputAct() })
    }

    private fun DIV.textFieldWithLabel(id: String, label: String, value: String, inputAct: INPUT.() -> Unit) {
        div {
            label("fields") {
                htmlFor = id
                +label
            }
            textInput {
                this.id = id
                this.value = value
                inputAct()
                onKeyUpFunction = { menu.activateSaveButton() }
                val input = getUnderlyingHtmlElement() as HTMLInputElement
                disableUnDisableForReleaseStartAndEnd(input, input)
            }
        }
    }

    private fun DIV.textAreaWithLabel(id: String, label: String, value: String) {
        div {
            label("fields") {
                htmlFor = id
                +label
            }
            textArea {
                this.id = id
                +value
                onKeyUpFunction = { menu.activateSaveButton() }
                val htmlTextAreaElement = getUnderlyingHtmlElement() as HTMLTextAreaElement
                //for what disableUnDisableForReleaseStartAndEnd needs, title and disabled, it is ok to make the unsafe cast
                //TODO change in case https://github.com/Kotlin/kotlinx.html/issues/87 is implemented
                val input = htmlTextAreaElement.unsafeCast<HTMLInputElement>()
                disableUnDisableForReleaseStartAndEnd(input, htmlTextAreaElement)
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
        div("contextMenu") {
            id = "$idPrefix$CONTEXT_MENU_SUFFIX"
            div("succeeded") {
                title = "Forcibly sets the state of this command to Succeeded, to be used with care."
                i("material-icons") { span() }
                span {
                    +"Set Command to Succeeded"
                }
                getUnderlyingHtmlElement().addClickEventListener {
                    transitionToSucceededIfOk(project, index)
                }
            }
        }

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

    private fun transitionToSucceededIfOk(project: Project, index: Int) {
        if (project.commands[index] is ReleaseCommand) {
            if (notAllOtherCommandsSucceeded(project, index)) {
                val succeeded = CommandState.Succeeded::class.simpleName
                showDialog(
                    "You cannot set this command to the state $succeeded because not all other commands of this project have $succeeded yet." +
                        "\n\n" +
                        "Do you want to set all other commands forcibly to $succeeded as well?"
                ).then { setAllToSucceeded ->
                    if (setAllToSucceeded) {
                        transitionAllCommandsToSucceeded(project)
                        menu.activateSaveButton()
                    }
                }
                return
            }
        }
        transitionToSucceeded(project, index)
        menu.activateSaveButton()
    }

    private fun transitionAllCommandsToSucceeded(project: Project) {
        project.commands.forEachIndexed { index, _ ->
            transitionToSucceeded(project, index)
        }
        releasePlan.getSubmodules(project.id).forEach {
            transitionAllCommandsToSucceeded(releasePlan.getProject(it))
        }
    }

    private fun transitionToSucceeded(project: Project, index: Int) {
        changeStateOfCommand(project, index, CommandState.Succeeded, stateToTitle(CommandState.Succeeded)) { _, _ ->
            //we don't check transition here, the user has to know what she does (at least for now)
            CommandState.Succeeded
        }
    }

    private fun notAllOtherCommandsSucceeded(project: Project, index: Int?): Boolean {
        return project.commands.asSequence()
            .mapWithIndex()
            .any { (i, _) ->
                (index == null || i != index) && getCommandState(
                    project.id,
                    i
                ) !== CommandState.Succeeded
            }
            || releasePlan.getSubmodules(project.id)
            .any { notAllOtherCommandsSucceeded(releasePlan.getProject(it), null) }
    }

    private fun DIV.appendJenkinsMavenReleasePluginField(idPrefix: String, command: JenkinsMavenReleasePlugin) {
        fieldNextDevVersion(idPrefix, command, command.nextDevVersion)
    }

    private fun DIV.fieldNextDevVersion(
        idPrefix: String,
        command: Command,
        nextDevVersion: String
    ) {
        textFieldWithLabel("$idPrefix${Gui.NEXT_DEV_VERSION_SUFFIX}", "Next Dev Version", nextDevVersion) {
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
        textFieldReadOnlyWithLabel(
            "$idPrefix:groupId",
            "Dependency",
            command.projectId.identifier,
            { projectId(command.projectId) })
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
        const val RELEASE_ID_HTML_ID = "releaseId"
        const val HIDE_MESSAGES_HTML_ID = "hideMessages"

        const val DEACTIVATE_SUFFIX = ":deactivate"
        const val DEACTIVATE_ALL_SUFFIX = ":deactivateAll"
        const val SLIDER_SUFFIX = ":slider"
        const val DISABLED_RELEASE_IN_PROGRESS = "disabled due to release which is in progress."
        const val DISABLED_RELEASE_SUCCESS = "Release successful, use a new pipeline for a new release."
        const val NEXT_DEV_VERSION_SUFFIX = ":nextDevVersion"
        const val STATE_SUFFIX = ":state"
        const val TITLE_SUFFIX = ":title"
        const val CONTEXT_MENU_SUFFIX = ":contextMenu"

        private const val STATE_WAITING = "Wait for dependent projects to complete."
        const val STATE_READY = "Ready to be queued for execution."
        const val STATE_READY_TO_BE_TRIGGER = "Ready to be re-scheduled"
        const val STATE_QUEUEING = "Currently queueing the job."
        const val STATE_IN_PROGRESS = "Job is running."
        const val STATE_SUCCEEDED = "Job completed successfully."
        const val STATE_FAILED = "Job completed successfully."
        private const val STATE_DEACTIVATED = "Currently deactivated, click to activate"
        const val STATE_DISABLED = "Command disabled, cannot be reactivated."

        fun getCommandId(project: Project, index: Int) = getCommandId(project.id, index)
        fun getCommandId(projectId: ProjectId, index: Int) = "${projectId.identifier}:$index"
        fun getCommand(project: Project, index: Int) = getCommand(project.id, index)
        fun getCommand(projectId: ProjectId, index: Int): HTMLElement = elementById(getCommandId(projectId, index))

        fun getCommandState(projectId: ProjectId, index: Int): CommandState {
            return getCommand(projectId, index).asDynamic().state as CommandState
        }

        fun disableUnDisableForReleaseStartAndEnd(input: HTMLInputElement, titleElement: HTMLElement) {
            Menu.registerForReleaseStartEvent {
                input.asDynamic().oldDisabled = input.disabled
                input.disabled = true
                titleElement.setTitleSaveOld(DISABLED_RELEASE_IN_PROGRESS)
            }
            Menu.registerForReleaseEndEvent { success ->
                if (success) {
                    titleElement.title = DISABLED_RELEASE_SUCCESS
                } else {
                    input.disabled = input.asDynamic().oldDisabled as Boolean
                    titleElement.title = titleElement.getOldTitle()
                }
            }
        }

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
                    val commandTitle = elementById(commandId + TITLE_SUFFIX)
                    throw IllegalStateException(
                        "Cannot change the state of the command ${commandTitle.innerText} (${index + 1}. command) " +
                            "of the project ${project.id.identifier}",
                        e
                    )
                }
            }
        }

        private fun changeStateOfCommand(
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
            elementById("$commandId$STATE_SUFFIX").title = title
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


        fun getReleaseState() = elementById(Gui.PIPELINE_HTML_ID).asDynamic().state as ReleaseState

        fun changeReleaseState(newState: ReleaseState) {
            val pipeline = elementById(Gui.PIPELINE_HTML_ID).asDynamic()
            pipeline.state = getReleaseState().checkTransitionAllowed(newState)
        }

        private fun stateToTitle(state: CommandState) = when (state) {
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
