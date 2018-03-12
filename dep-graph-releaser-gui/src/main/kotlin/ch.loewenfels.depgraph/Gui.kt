package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.div
import kotlin.browser.document

class Gui(private val releasePlan: ReleasePlan) {

    fun load() {
        document.title = "Release " + releasePlan.rootProjectId.identifier

        elementById("gui").append {
            val itr = releasePlan.iterator().toPeekingIterator()
            var level: Int
            while (itr.hasNext()) {
                val project = itr.next()
                level = project.level

                div("level l$level") {
                    project(project)
                    while (hasNextOnTheSameLevel(itr, level)) {
                        project(itr.next())
                    }
                }
            }
            showMessage("${releasePlan.projects.size} projects loaded")
        }
    }

    private fun hasNextOnTheSameLevel(
        itr: PeekingIterator<Project>,
        level: Int
    ) = itr.hasNext() && level == itr.peek().level

    private fun DIV.project(project: Project) {
        val id = project.id
        div("project") {
            this.id = id.identifier
            div("title") {
                toggle("${id.identifier}:disableAll", project.commands.any { it.state !is CommandState.Deactivated })
                span {
                    projectId(id)
                }
            }
            div("fields") {
                fieldReadOnlyWithLabel("${id.identifier}:currentVersion", "Current Version", project.currentVersion)
                fieldWithLabel("${id.identifier}:releaseVersion", "Release Version", project.releaseVersion)
            }
            commands(project)
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
                    fieldsForCommand("${project.id.identifier}:$index", command)
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
            input(InputType.text) {
                this.id = id
                value = text
                inputAct()
            }
        }
    }

    private fun DIV.fieldsForCommand(idPrefix: String, command: Command) {
        val cssClass = when (command) {
            is ReleaseCommand -> "release"
            else -> ""
        }
        toggle("$idPrefix:disable", command.state !is CommandState.Deactivated, cssClass)

        when (command) {
            is JenkinsMavenReleasePlugin -> {
                fieldWithLabel("$idPrefix:nextDevVersion", "Next Dev Version", command.nextDevVersion)
            }
            is JenkinsUpdateDependency -> {
                fieldReadOnlyWithLabel(
                    "$idPrefix:groupId",
                    "Dependency",
                    command.projectId.identifier,
                    { projectId(command.projectId) })
            }
        }

    }

    private fun DIV.toggle(id: String, checked: Boolean, checkboxCssClass: String = "") {
        label("toggle") {
            input(InputType.checkBox, classes = checkboxCssClass) {
                this.id = id
                this.checked = checked
                onClick = "toggle('$id')"
            }
            span("slider")
        }
    }
}
