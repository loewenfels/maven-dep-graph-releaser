package ch.loewenfels.depgraph.html

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
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
            input {
                type = InputType.text
                this.disabled = disabled
                this.id = id
                value = text
            }
        }
    }


    private fun DIV.fieldsForCommand(idPrefix: String, command: Command) {
        toggle("$idPrefix:disable", command.state !is CommandState.Deactivated)
        when (command) {
            is JenkinsMavenReleasePlugin -> {
                fieldWithLabel("$idPrefix:nextDevVersion", "Next Dev Version", command.nextDevVersion)
            }
            is JenkinsUpdateDependency -> {
                fieldReadOnlyWithLabel("$idPrefix:groupId", "Dependency", command.projectId.identifier)
            }
        }

    }

    private fun DIV.toggle(id: String, checked: Boolean) {
        label("toggle") {
            input {
                type = InputType.checkBox
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
            css()
            javascript()
        }
    }

    private fun HEAD.javascript() {
        script("text/javascript") {
            val scanner = Scanner(this::class.java.getResourceAsStream("/script.js"), Charsets.UTF_8.name()).useDelimiter("\\A")
            val script = scanner.use {
                if (it.hasNext()) {
                    it.next()
                } else {
                    ""
                }
            }
            unsafe {
                raw("\n" + script)
            }
        }
    }

    private fun HEAD.css() {
        style {
            unsafe {
                raw("""
         body {
            font-family: "Arimo", sans-serif;
            font-size:12px;
        }
        div.project {
            border:1px solid #CCC;
            background: #fff;
            margin: 20px auto;
            display: block;
            width: 300px;
            padding:20px 15px;
            border-radius: 2px 2px 2px 2px;
            -webkit-box-shadow: 0 1px 4px
            rgba(0, 0, 0, 0.3), 0 0 40px
            rgba(0, 0, 0, 0.1) inset;
            -moz-box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3), 0 0 40px rgba(0, 0, 0, 0.1) inset;
            box-shadow: 0 1px 4px
            rgba(0, 0, 0, 0.3), 0 0 40px
            rgba(0, 0, 0, 0.1) inset;
        }
        div.title {
            font-size:1.2em;
            font-weight: bold;
            margin-bottom:5px;
        }
        div.commandTitle {
            font-weight:bold;
            margin-top:10px;
            margin-bottom: 3px;
        }
        label.fields {
            display: inline-block;
            width: 100px;
            text-align: right;
            padding-right: 6px;
        }
        input {
            width:150px;
        }
        label.toggle {
            position: relative;
            display: inline-block;
            width: 35px;
            height: 20px;
            float:right;
        }
        label.toggle input {
            display:none;
        }
        label.toggle .slider {
            position: absolute;
            cursor: pointer;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background-color: #ccc;
            -webkit-transition: .4s;
            transition: .4s;
            border-radius: 34px;
        }
        label.toggle .slider:before {
          position: absolute;
          content: "";
          height: 12px;
          width: 12px;
          left: 4px;
          bottom: 4px;
          background-color: white;
          -webkit-transition: .4s;
          transition: .4s;
          border-radius: 50%;
        }

        label.toggle input:checked + .slider {
          background-color: #2196F3;
        }
        input:focus + .slider {
          box-shadow: 0 0 1px #2196F3;
        }

        input:checked + .slider:before {
          -webkit-transform: translateX(14px);
          -ms-transform: translateX(14px);
          transform: translateX(14px);
        }
    """)
            }
        }
    }
}
