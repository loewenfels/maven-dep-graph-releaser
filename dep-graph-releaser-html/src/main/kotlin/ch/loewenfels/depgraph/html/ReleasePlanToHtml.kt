package ch.loewenfels.depgraph.html

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.kbox.PeekingIterator
import ch.tutteli.kbox.toPeekingIterator
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.util.*

class ReleasePlanToHtml {
    fun createHtml(releasePlan: ReleasePlan): StringBuilder {
        val sb = StringBuilder()
        sb.appendHTML().html {
            head(releasePlan)
            body(releasePlan)
        }
        return sb
    }

    private fun HTML.body(releasePlan: ReleasePlan) {
        body {
            var level: Int
            val itr = releasePlan.iterator().toPeekingIterator()
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
                div("commandTitle") { +command::class.java.simpleName }
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

    private fun HTML.head(releasePlan: ReleasePlan) {
        head {
            title {
                +"Release ${releasePlan.rootProjectId.identifier}"
            }
            style {
                unsafeRawFromFileFile("/style.css")
            }
            javascript(releasePlan)
        }
    }

    private fun HEAD.javascript(releasePlan: ReleasePlan) {
        script("text/javascript") {
            val dependents = releasePlan.dependents.entries.joinToString(",\n") { (k, v) ->
                """"${k.identifier}": [${v.joinToString { "\"${it.identifier}\"" }}]"""
            }
            unsafeRaw("\nvar releasePlan = {$dependents}")

            unsafeRawFromFileFile("/script.js")
        }
    }

    private fun HTMLTag.unsafeRawFromFileFile(file: String) {
        val scanner = Scanner(this::class.java.getResourceAsStream(file), Charsets.UTF_8.name())
            .useDelimiter("\\A")

        val fileContent = scanner.use {
            if (it.hasNext()) {
                it.next()
            } else {
                ""
            }
        }
        unsafeRaw("\n" + fileContent)
    }

    private fun HTMLTag.unsafeRaw(content: String) {
        unsafe { raw(content) }
    }
}
