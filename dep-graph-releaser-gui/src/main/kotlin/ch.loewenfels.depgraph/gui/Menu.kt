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
    private val userIcon get() = elementById("user.icon")
    private val userName get() = elementById("user.name")
    private val saveButton get() = elementById("save")
    private val downloadButton get() = elementById("download")
    private val dryRunButton get() = elementById("dryRun")
    private val buildButton get() = elementById("build")
    private val jenkinsUrl = publishJobUrl?.substringBefore("/job/")
    private lateinit var usernameToken: UsernameToken

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
        return if (jenkinsUrl == null) {
            disableButtonsDueToNoPublishUrl()
            Promise.resolve(Unit)
        } else {
            window.fetch("$jenkinsUrl/me/configure", createFetchInitWithCredentials())
                .then(::checkStatusOkOr403)
                .then { body: String? ->
                    if (body == null) {
                        val info = "You need to log in if you want to use other functionality than Download."
                        disableButtonsDueToNoAuth(info, info)
                    } else {
                        val (username, name, apiToken) = extractNameAndApiToken(body)
                        userName.innerText = name
                        userIcon.innerText = "verified_user"
                        userButton.removeClass(DEACTIVATED)
                        usernameToken = UsernameToken(username, apiToken)
                    }
                }
        }
    }

    private fun disableButtonsDueToNoPublishUrl() {
        val titleButtons = "You need to specify publishJob if you want to use other functionality than Download."
        disableButtonsDueToNoAuth(
            titleButtons, titleButtons +
                "\nAn example: ${window.location}&publishJob=jobUrl" +
                "\nwhere you need to replace jobUrl accordingly."
        )
    }

    private fun disableButtonsDueToNoAuth(titleButtons: String, info: String) {
        showInfo(info)
        userButton.title = titleButtons
        userButton.addClass(DEACTIVATED)
        userName.innerText = "Anonymous"
        userIcon.innerText = "error"
        listOf(saveButton, dryRunButton, buildButton).forEach { it.disable(titleButtons) }
    }

    private fun extractNameAndApiToken(body: String): Triple<String, String, String> {
        val usernameMatch = usernameRegex.find(body) ?: throw IllegalStateException("Could not find username")
        val fullNameMatch = fullNameRegex.find(body) ?: throw IllegalStateException("Could not find user's name")
        val apiTokenMatch = apiTokenRegex.find(body) ?: throw IllegalStateException("Could not find API token")
        return Triple(usernameMatch.groupValues[1], fullNameMatch.groupValues[1], apiTokenMatch.groupValues[1])
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
        saveButton.title = "Publish changed json file and change location"
    }

    private fun save() {
        val releasePlanJson = JSON.parse<ReleasePlanJson>(body)
        val changed = applyChanges(releasePlanJson)
        if (changed) {
            if (publishJobUrl != null) {
                val newFileName = "release-${generateUniqueId()}"
                val newJson = JSON.stringify(releasePlanJson)
                publish(newJson, newFileName, publishJobUrl, usernameToken)
            } else {
                showError(
                    IllegalStateException(
                        "Save button should not be activate if no publish job url was specified.\nPlease report a bug."
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
        private val fullNameRegex = Regex("<input[^>]+name=\"_\\.fullName\"[^>]+value=\"([^\"]+)\"")
        private val apiTokenRegex = Regex("<input[^>]+name=\"_\\.apiToken\"[^>]+value=\"([^\"]+)\"")
        private val usernameRegex = Regex("<a[^>]+href=\"[^\"]*/user/([^\"]+)\"")
    }
}
