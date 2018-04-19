package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.loewenfels.depgraph.toPeekingIterator
import kotlinx.html.*
import kotlinx.html.dom.append
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.browser.document

class Gui(private val releasePlan: ReleasePlan, private val menu: Menu) {
    private val toggler = Toggler(releasePlan, menu)

    fun load() {
        document.title = "Release " + releasePlan.rootProjectId.identifier
        setUpMessages(releasePlan.warnings, "warnings", { showWarning(it) })
        setUpMessages(releasePlan.infos, "infos", { showInfo(it) })
        setUpConfig(releasePlan.config)
        setUpProjects()
        toggler.registerToggleEvents()
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
    }

    private fun setUpConfig(config: Map<ConfigKey, String>) {
        //TODO add description for each property
        elementById("config").append {
            div {
                ConfigKey.all().forEach { key ->
                    fieldWithLabel("config-$key", key.asString(), config[key] ?: "")
                }
            }
        }
    }

    private fun setUpProjects() {
        val set = hashSetOf<ProjectId>()
        elementById("pipeline").append {
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
            val div = getUnderlyingHtmlElement()
            div.asDynamic().project = project

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
                        "$identifier$DISABLE_ALL_SUFFIX",
                        "disable all commands",
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
                    fieldReadOnlyWithLabel(
                        "$identifier:currentVersion",
                        "Current Version",
                        project.currentVersion
                    )
                    fieldWithLabel("$identifier:releaseVersion", "Release Version", project.releaseVersion)
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
                div("commandTitle") { +command::class.simpleName!! }
                div("fields") {
                    fieldsForCommand(commandId, project.id, command)
                }
            }
        }
    }

    private fun DIV.fieldWithLabel(id: String, label: String, text: String) {
        fieldWithLabel(id, label, text, {})
    }

    private fun DIV.fieldReadOnlyWithLabel(id: String, label: String, text: String, inputAct: INPUT.() -> Unit = {}) {
        fieldWithLabel(id, label, text, { disabled = true; inputAct() })
    }

    private fun DIV.fieldWithLabel(id: String, label: String, text: String, inputAct: INPUT.() -> Unit) {
        div {
            label("fields") {
                htmlFor = id
                +label
            }
            textInput {
                this.id = id
                value = text
                inputAct()
                val input = getUnderlyingHtmlElement() as HTMLInputElement
                input.addEventListener("keyup", { menu.activateSaveButton() })
                disableUnDisableForReleaseStartAndEnd(input, input)
            }
        }
    }

    private fun DIV.fieldsForCommand(idPrefix: String, projectId: ProjectId, command: Command) {
        val cssClass = when (command) {
            is ReleaseCommand -> "release"
            else -> ""
        }

        toggle(
            "$idPrefix$DISABLE_SUFFIX",
            "disable ${command::class.simpleName}",
            command.state !is CommandState.Deactivated,
            command.state === CommandState.Disabled,
            cssClass
        )
        div("state") {
            id = "$idPrefix:state"
            i("material-icons") {
                span()
                id = "$idPrefix:status.icon"
            }
        }

        when (command) {
            is JenkinsMavenReleasePlugin ->
                appendJenkinsMavenReleasePluginField(idPrefix, command)
            is JenkinsMultiMavenReleasePlugin ->
                appendJenkinsMultiMavenReleasePluginFields(idPrefix, projectId, command)
            is JenkinsUpdateDependency ->
                appendJenkinsUpdateDependencyField(idPrefix, command)
            else ->
                showError("unknown command found, cannot display its fields.\n$command")
        }
    }

    private fun DIV.appendJenkinsMavenReleasePluginField(idPrefix: String, command: JenkinsMavenReleasePlugin) {
        fieldNextDevVersion(idPrefix, command, command.nextDevVersion)
    }

    private fun DIV.fieldNextDevVersion(
        idPrefix: String,
        command: Command,
        nextDevVersion: String
    ) {
        fieldWithLabel("$idPrefix:nextDevVersion", "Next Dev Version", nextDevVersion) {
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
        fieldReadOnlyWithLabel(
            "$idPrefix:groupId",
            "Dependency",
            command.projectId.identifier,
            { projectId(command.projectId) })
    }

    private fun DIV.toggle(
        id: String,
        title: String,
        checked: Boolean,
        disabled: Boolean,
        checkboxCssClass: String = ""
    ) {
        label("toggle") {
            checkBoxInput(classes = checkboxCssClass) {
                this.id = id
                this.checked = checked && !disabled
                this.disabled = disabled
            }
            span("slider") {
                this.id = "$id:slider"
                this.title = title
                if (disabled) {
                    this.title = "disabled, cannot be reactivated"
                }
            }
        }
    }

    companion object {
        const val DISABLE_SUFFIX = ":disable"
        const val DISABLE_ALL_SUFFIX = ":disableAll"
        private const val DISABLED_DUE_TO_RELEASE = "disabled due to release which is in progress."

        fun getCommandId(project: Project, index: Int) = "${project.id.identifier}:$index"

        fun stateToCssClass(state: CommandState) = when (state) {
            is CommandState.Waiting -> "waiting"
            CommandState.Ready -> "ready"
            CommandState.InProgress -> "inProgress"
            CommandState.Succeeded -> "succeeded"
            is CommandState.Failed -> "failed"
            is CommandState.Deactivated -> "deactivated"
            CommandState.Disabled -> "disabled"
        }

        fun disableUnDisableForReleaseStartAndEnd(input: HTMLInputElement, titleElement: HTMLElement) {
            Menu.registerForReleaseStartEvent {
                input.asDynamic().oldDisabled = input.disabled
                input.disabled = true
                titleElement.asDynamic().oldTitle = titleElement.title
                titleElement.title = DISABLED_DUE_TO_RELEASE
            }
            Menu.registerForReleaseEndEvent {
                input.disabled = input.asDynamic().oldDisabled as Boolean
                titleElement.title = titleElement.asDynamic().oldTitle as String
            }

        }
    }
}
