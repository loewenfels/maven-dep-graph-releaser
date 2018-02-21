package ch.loewenfels.depgraph.html

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
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
            for (project in releasePlan.iterator()) {
                project(project)
            }
        }
    }

    private fun BODY.project(project: Project) {
        val id = project.id
        div("project") {
            this.id = id.identifier
            div("title") {
                toggle("${id.identifier}:disableAll", project.commands.any { it.state !is CommandState.Deactivated })
                span {
                    if (id is MavenProjectId) {
                        title = id.identifier
                        +id.artifactId
                    } else {
                        +id.identifier
                    }
                }

            }
            div("fields") {
                fieldReadOnlyWithLabel("${id.identifier}:currentVersion", "Current Version", project.currentVersion)
                fieldWithLabel("${id.identifier}:releaseVersion", "Release Version", project.releaseVersion)
            }
            commands(project)
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
        fieldWithLabel(id, label, text, false)
    }

    private fun DIV.fieldReadOnlyWithLabel(id: String, label: String, text: String) {
        fieldWithLabel(id, label, text, true)
    }

    private fun DIV.fieldWithLabel(id: String, label: String, text: String, disabled: Boolean) {
        div {
            label("fields") {
                htmlFor = id
                +label
            }
            input(InputType.text) {
                this.disabled = disabled
                this.id = id
                value = text
            }
        }
    }


    private fun DIV.fieldsForCommand(idPrefix: String, command: Command) {
        val cssClass = when(command){
            is ReleaseCommand -> "release"
            else -> ""
        }
        toggle("$idPrefix:disable", command.state !is CommandState.Deactivated, cssClass)

        when (command) {
            is JenkinsMavenReleasePlugin -> {
                fieldWithLabel("$idPrefix:nextDevVersion", "Next Dev Version", command.nextDevVersion)
            }
            is JenkinsUpdateDependency -> {
                fieldReadOnlyWithLabel("$idPrefix:groupId", "Dependency", command.projectId.identifier)
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
            script("text/javascript") {
                unsafeRawFromFileFile("/script.js")
            }
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
        unsafe {
            raw("\n" + fileContent)
        }
    }
}
