package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import org.w3c.dom.HTMLElement
import org.w3c.fetch.Response
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.hasClass
import kotlin.dom.removeClass
import kotlin.js.*

external fun encodeURIComponent(encodedURI: String): String

class Menu(
    private val body: String,
    private val publishJobUrl: String?
) {
    private val userButton get() = elementById("user")
    private val saveButton get() = elementById("save")
    private val downloadButton get() = elementById("download")
    private val dryRunButton get() = elementById("dryRun")
    private val buildButton get() = elementById("build")
    private val jenkinsUrl = publishJobUrl?.substringBefore("/job/")
    private var apiToken: String? = null

    init {
        window.onbeforeunload = {
            if (!saveButton.hasClass(DEACTIVATED)) {
                "Your changes will be lost, sure you want to leave the page?"
            } else {
                null
            }
        }
        retrieveUserAndApiToken()
            .then {
                initSaveAndDownloadButton()
                initDryRunAndBuildButton()
            }
    }

    private fun retrieveUserAndApiToken(): Promise<Unit> {
        //cannot login if no jenkins url is given
        if (jenkinsUrl == null) {
            val info = "You need to specify publishJob if you want to use other functionality than Download."
            showInfo(
                info +
                    "\nAn example: ${window.location}&publishJob=jobUrl" +
                    "\nwhere you need to replace jobUrl accordingly."
            )
            listOf(saveButton, dryRunButton, buildButton).forEach { it.disable(info) }
            return Promise.resolve(Unit)
        }

        return window.fetch("$jenkinsUrl/me/configure", createFetchInitWithCredentials())
            .then(::checkStatusOkOr403)
            .then { body: String? ->
                if (body == null) {
                    val info = "You need to log in if you want to use other functionality than Download."
                    showInfo(info)
                    userButton.title = info
                    listOf(saveButton, dryRunButton, buildButton).forEach { it.disable(info) }
                } else {
                    val (name, apiToken) = extractNameAndApiToken(body)
                    elementById("user.name").innerText = name
                    elementById("user.icon").innerText = "verified_user"
                    userButton.removeClass(DEACTIVATED)
                    this.apiToken = apiToken
                }
            }
    }

    private fun extractNameAndApiToken(body: String): Pair<String, String> {
        val fullNameMatch = fullNameRegex.find(body) ?: throw IllegalStateException("Could not find username")
        val apiTokenMatch = apiTokenRegex.find(body) ?: throw IllegalStateException("Could not find API token")
        return fullNameMatch.groupValues[1] to apiTokenMatch.groupValues[1]
    }

    private fun checkStatusOkOr403(response: Response): Promise<String?> {
        return response.text().then { text ->
            if (response.status == 403.toShort()) {
                null
            } else {
                check(response.ok) { "response was not ok, ${response.status}: ${response.statusText}\n$text" }
                text
            }
        }
    }

    private fun initSaveAndDownloadButton() {
        deactivateSaveButton()
        if (publishJobUrl != null) {
            saveButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                save()
            }
        }

        downloadButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            download()
        }
    }

    private fun initDryRunAndBuildButton() {
        dryRunButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            //TODO implement
        }
        buildButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            //TODO implement
        }
    }

    private fun HTMLElement.disable(reason: String) {
        this.addClass(DISABLED)
        this.title = reason
    }

    private fun HTMLElement.addClickEventListenerIfNotDeactivatedNorDisabled(action: () -> Unit) {
        addClickEventListener {
            if (hasClass(DEACTIVATED) || hasClass(DISABLED)) return@addClickEventListener
            action()
        }
    }

    private fun download() {
        val releasePlanJson = JSON.parse<ReleasePlanJson>(body)
        applyChanges(releasePlanJson)
        val json = JSON.stringify(releasePlanJson)
        download(json)
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

    private fun deactivateSaveButton() {
        if (saveButton.hasClass(DISABLED)) return
        saveButton.addClass(DEACTIVATED)
        saveButton.title = "Nothing to save, no changes were made"
    }

    fun activateSaveButton() {
        if (saveButton.hasClass(DISABLED)) return
        saveButton.removeClass(DEACTIVATED)
        saveButton.title = "Publish changed json file and reload"
    }

    private fun save() {
        val releasePlanJson = JSON.parse<ReleasePlanJson>(body)
        val changed = applyChanges(releasePlanJson)
        if (changed) {
            if (publishJobUrl != null) {
                val newFileName = "release-${generateUniqueId()}"
                val newJson = JSON.stringify(releasePlanJson)
                publish(newJson, newFileName, publishJobUrl)
            } else {
                showError(
                    IllegalStateException(
                        "save button should not be activate if now publish job url was specified.\nPlease report a bug"
                    )
                )
            }
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

    companion object {
        private const val DEACTIVATED = "deactivated"
        private const val DISABLED = "disabled"
        private val fullNameRegex = Regex("<input[^>]+name=\"_\\.fullName\"[^>]+value=\"([^\"]+)\"[^>]*>")
        private val apiTokenRegex = Regex("<input[^>]+name=\"_\\.apiToken\"[^>]+value=\"([^\"]+)\"[^>]*>")
    }
}
