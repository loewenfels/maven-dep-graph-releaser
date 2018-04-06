package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.loewenfels.depgraph.toPeekingIterator
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.div
import kotlin.browser.document

class Gui(private val releasePlan: ReleasePlan) {
    private val toggler = Toggler(releasePlan)
    fun load() {
        document.title = "Release " + releasePlan.rootProjectId.identifier
        setUpProjects()
        releasePlan.warnings.forEach(::showWarning)
        releasePlan.infos.forEach(::showInfo)
    }

    private fun setUpProjects() {
        val set = hashSetOf<ProjectId>()
        elementById("gui").append {
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
        showMessage("Projects involved: $involvedProjects")
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
                if(project.isSubmodule) "submodule" else "",
                if(!hasCommands) "withoutCommands" else "",
                if(releasePlan.hasSubmodules(project.id)) "withSubmodules" else ""
            )

            val id = project.id
            this.id = id.identifier

            div("title") {
                if(hasCommands) {
                    toggle(
                        "${id.identifier}:disableAll",
                        project.commands.any { it.state !is CommandState.Deactivated },
                        false
                    )
                }
                span {
                    projectId(id)
                }
            }
            if (!project.isSubmodule) {
                div("fields") {
                    fieldReadOnlyWithLabel(
                        "${id.identifier}:currentVersion",
                        "Current Version",
                        project.currentVersion
                    )
                    fieldWithLabel("${id.identifier}:releaseVersion", "Release Version", project.releaseVersion)
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
                classes = setOf("command", stateToCssClass(command.state))
                div("commandTitle") { +command::class.simpleName!! }
                div("fields") {
                    fieldsForCommand("${project.id.identifier}:$index", project.id, command)
                }
            }
        }
    }

    private fun stateToCssClass(state: CommandState) = when (state) {
        is CommandState.Waiting -> "waiting"
        CommandState.Ready -> "ready"
        CommandState.InProgress -> "inProgress"
        CommandState.Succeeded -> "succeeded"
        is CommandState.Failed -> "failed"
        is CommandState.Deactivated -> "deactivated"
        CommandState.Disabled -> "disabled"
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
            }
        }
    }

    private fun DIV.fieldsForCommand(
        idPrefix: String,
        projectId: ProjectId,
        command: Command
    ) {
        val cssClass = when (command) {
            is ReleaseCommand -> "release"
            else -> ""
        }
        toggle(
            "$idPrefix:disable",
            command.state !is CommandState.Deactivated,
            command.state === CommandState.Disabled,
            cssClass
        )

        when (command) {
            is JenkinsMavenReleasePlugin -> appendJenkinsMavenReleasePluginField(idPrefix, command)
            is JenkinsMultiMavenReleasePlugin -> appendJenkinsMultiMavenReleasePluginFields(idPrefix, projectId, command)
            is JenkinsUpdateDependency -> appendJenkinsUpdateDependencyField(idPrefix, command)

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

    private fun DIV.toggle(id: String, checked: Boolean, disabled: Boolean, checkboxCssClass: String = "") {
        label("toggle") {
            checkBoxInput(classes = checkboxCssClass) {
                this.id = id
                this.checked = checked && !disabled
                this.disabled = disabled
                val checkbox = getUnderlyingHtmlElement()
                checkbox.addEventListener("click", { toggler.toggle(id)})
            }
            span("slider") {
                if (disabled) {
                    this.title = "disabled, cannot be reactivated"
                }
            }
        }
    }
}
