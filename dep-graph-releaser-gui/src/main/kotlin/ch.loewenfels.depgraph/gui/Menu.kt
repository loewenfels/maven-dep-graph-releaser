package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import org.w3c.dom.HTMLElement
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.hasClass
import kotlin.dom.removeClass

external fun encodeURIComponent(encodedURI: String): String

class Menu(private val body: String) {

    private val saveButton get() = elementById("save")
    private val dryRunButton get() = elementById("dryRun")
    private val buildButton get() = elementById("build")

    init {
        window.onbeforeunload = {
            if (!saveButton.hasClass(DEACTIVATED)) {
                "Your changes will be lost, sure you want to leave the page?"
            } else {
                null
            }
        }
        initSaveButton()
        initDryRunAndBuildButton()
    }

    private fun initDryRunAndBuildButton() {
        dryRunButton.addEventListener("click", {
            //TODO implement
        })
        buildButton.addEventListener("click", {
            //TODO implement
        })
    }

    private fun initSaveButton() {
        saveButton.addEventListener("click", {
            //nothing to do if deactivated
            if (saveButton.hasClass(DEACTIVATED)) return@addEventListener
            save()
        })
        deactivateSaveButton()
    }

    private fun deactivateSaveButton() {
        saveButton.addClass(DEACTIVATED)
        saveButton.title = "nothing to save, no changes were made"
    }

    fun activateSaveButton() {
        saveButton.removeClass(DEACTIVATED)
        saveButton.title = "Download changed json file"
    }

    private fun save() {
        val releasePlanJson = JSON.parse<ReleasePlanJson>(body)
        var changed = applyChanges(releasePlanJson)

        if (changed) {
            val newJson = JSON.stringify(releasePlanJson)
            //TODO make configurable if download is used or a publish function
            download(newJson)
        } else {
            showInfo("Seems like all changes have been reverted manually. Will not save anything.")
        }
        deactivateSaveButton()
    }

    private fun applyChanges(releasePlanJson: ReleasePlanJson): Boolean {
        var changed = false
        releasePlanJson.projects.forEach { project ->
            val mavenProjectId = createProjectId(project.id)
            val releaseVersionChanged = replaceReleaseVersionIfChanged(project, mavenProjectId)
            changed = changed || releaseVersionChanged

            project.commands.forEachIndexed { index, command ->
                val stateChanged = replaceStateIfChanged(command, mavenProjectId, index)
                val fieldsChanged = replaceFieldsIfChanged(command, mavenProjectId, index)
                changed = changed || stateChanged || fieldsChanged
            }
        }
        return changed
    }

    private fun replaceReleaseVersionIfChanged(project: ProjectJson, mavenProjectId: ProjectId): Boolean {
        val input = getTextFieldOrNull("${mavenProjectId.identifier}:releaseVersion")
        if (input != null && project.releaseVersion != input.value) {
            project.asDynamic().releaseVersion = input.value
            return true
        }
        return false
    }

    private fun replaceStateIfChanged(command: GenericType<Command>, mavenProjectId: ProjectId, index: Int): Boolean {
        val commandStateJson = command.p.state.unsafeCast<CommandStateJson>()
        val state = commandStateJson.state.unsafeCast<String>()
        if (state != CommandStateJson.State.Deactivated.name && state != CommandStateJson.State.Disabled.name) {
            val checkbox = getCheckbox("${mavenProjectId.identifier}:$index:disable")
            if (!checkbox.checked) {
                val previous = JSON.parse<CommandStateJson>(JSON.stringify(commandStateJson))
                commandStateJson.asDynamic().state = CommandStateJson.State.Deactivated.name
                commandStateJson.asDynamic().previous = previous
                return true
            }
        }
        return false
    }

    private fun replaceFieldsIfChanged(command: GenericType<Command>, mavenProjectId: ProjectId, index: Int): Boolean {
        return when (command.t) {
            JENKINS_MAVEN_RELEASE_PLUGIN, JENKINS_MULTI_MAVEN_RELEASE_PLUGIN ->
                checkIfNextVersionChanged(command.p, mavenProjectId, index)
            JENKINS_UPDATE_DEPENDENCY -> false //nothing to do
            else -> throw UnsupportedOperationException("${command.t} is not supported.")
        }
    }

    private fun checkIfNextVersionChanged(command: Command, mavenProjectId: ProjectId, index: Int): Boolean {
        val m2Command = command.unsafeCast<M2ReleaseCommand>()
        val input = getTextField("${mavenProjectId.identifier}:$index:nextDevVersion")
        if (m2Command.nextDevVersion != input.value) {
            m2Command.asDynamic().nextDevVersion = input.value
            return true
        }
        return false
    }

    private fun download(json: String) {
        val a = document.createElement("a") as HTMLElement
        a.setAttribute("href", "data:text/plain;charset=utf-8,${encodeURIComponent(json)}")
        a.setAttribute("download", "release.json")
        a.style.display = "none"
        document.body!!.appendChild(a)
        a.click()
        document.body!!.removeChild(a)
    }

    companion object {
        private const val DEACTIVATED = "deactivated"
    }
}
