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
        setUpProjects()
        releasePlan.warnings.forEach {
            showWarning(it)
        }
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
                    project(project)

                    set.add(project.id)
                    while (hasNextOnTheSameLevel(itr, level)) {
                        val nextProject = itr.next()
                        project(nextProject)
                        set.add(nextProject.id)
                    }
                }
            }

            releasePlan.iterator().asSequence().forEach { project ->
                val div = elementById(project.id.identifier).asDynamic()
                div.project = project
                div.dependents = releasePlan.getDependents(project.id)
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

    private fun hasNextOnTheSameLevel(
        itr: PeekingIterator<Project>,
        level: Int
    ) = itr.hasNext() && level == itr.peek().level


    private fun DIV.project(project: Project) {
        div("project") {
            val id = project.id
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
            textInput {
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
            checkBoxInput(classes = checkboxCssClass) {
                this.id = id
                this.checked = checked
                onClick = "toggle('$id')"
            }
            span("slider")
        }
    }
}
