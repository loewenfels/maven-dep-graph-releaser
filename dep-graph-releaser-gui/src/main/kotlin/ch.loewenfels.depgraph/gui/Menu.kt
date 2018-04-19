package ch.loewenfels.depgraph.gui

import org.w3c.dom.HTMLElement
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.hasClass
import kotlin.dom.removeClass

external fun encodeURIComponent(encodedURI: String): String

class Menu {
    private val userButton get() = elementById("user")
    private val userIcon get() = elementById("user.icon")
    private val userName get() = elementById("user.name")
    private val saveButton get() = elementById("save")
    private val downloadButton get() = elementById("download")
    private val dryRunButton get() = elementById("dryRun")
    private val releaseButton get() = elementById("release")
    private val settingsButton get() = elementById("settings")

    init {
        settingsButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            elementById("config").addClass("active")
        }
        elementById("config_close").addClickEventListener {
            elementById("config").removeClass("active")
        }
    }

    fun disableButtonsDueToNoPublishUrl() {
        val titleButtons = "You need to specify publishJob if you want to use other functionality than Download."
        disableButtonsDueToNoAuth(
            titleButtons, titleButtons +
                "\nAn example: ${window.location}&publishJob=jobUrl" +
                "\nwhere you need to replace jobUrl accordingly."
        )
    }

    fun disableButtonsDueToNoAuth(titleButtons: String, info: String) {
        showInfo(info)
        userButton.title = titleButtons
        userButton.addClass(DEACTIVATED)
        userName.innerText = "Anonymous"
        userIcon.innerText = "error"
        listOf(saveButton, dryRunButton, releaseButton).forEach { it.disable(titleButtons) }
    }

    fun setVerifiedUser(username: String, name: String) {
        userName.innerText = name
        userIcon.innerText = "verified_user"
        userButton.title = "Logged in as $username"
        userButton.removeClass(Menu.DEACTIVATED)
    }


    fun initDependencies(downloader: Downloader, publisher: Publisher?, releaser: Releaser?) {

        window.onbeforeunload = {
            if (!saveButton.hasClass(DEACTIVATED)) {
                "Your changes will be lost, sure you want to leave the page?"
            } else {
                null
            }
        }

        initSaveAndDownloadButton(downloader, publisher)
        initDryRunAndReleaseButton(releaser)
    }

    private fun initSaveAndDownloadButton(downloader: Downloader, publisher: Publisher?) {
        deactivateSaveButton()
        if (publisher != null) {
            saveButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                save(publisher)
            }
        }
        downloadButton.title = "Download the release.json"
        downloadButton.removeClass(DEACTIVATED)
        downloadButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            downloader.download()
        }
    }

    private fun initDryRunAndReleaseButton(releaser: Releaser?) {
        if (releaser != null) {
            activateReleaseButton()
            dryRunButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                //TODO implement
            }
            releaseButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                releaser.release()
            }
        }
    }

    private fun HTMLElement.addClickEventListenerIfNotDeactivatedNorDisabled(action: () -> Unit) {
        addClickEventListener {
            if (hasClass(DEACTIVATED) || hasClass(DISABLED)) return@addClickEventListener
            action()
        }
    }

    private fun HTMLElement.disable(reason: String) {
        this.addClass(DISABLED)
        this.title = reason
    }

    private fun HTMLElement.isDisabled() = hasClass(Menu.DISABLED)

    private fun HTMLElement.deactivate(reason: String) {
        if (saveButton.isDisabled()) return

        this.addClass(DEACTIVATED)
        this.title = reason
    }

    private fun deactivateSaveButton() {
        saveButton.deactivate("Nothing to save, no changes were made")
    }

    fun activateSaveButton() {
        if (saveButton.isDisabled()) return

        saveButton.removeClass(DEACTIVATED)
        saveButton.title = "Publish changed json file and change location"
        val saveFirst = "You need to save your changes first."
        dryRunButton.deactivate(saveFirst)
        releaseButton.deactivate(saveFirst)
    }

    private fun activateReleaseButton() {
        if (releaseButton.isDisabled()) return

        releaseButton.removeClass(DEACTIVATED)
        releaseButton.title = "Start a release based on this release plan."
    }

    private fun save(publisher: Publisher?) {
        if (publisher == null) {
            deactivateSaveButton()
            showError(
                IllegalStateException(
                    "Save button should not be activate if no publish job url was specified.\nPlease report a bug."
                )
            )
        }

        val changed = publisher.applyChanges()
        if (changed) {
            val newFileName = "release-${generateUniqueId()}"
            publisher.publish(newFileName)
                .then { deactivateSaveButton() }
        } else {
            showInfo("Seems like all changes have been reverted manually. Will not save anything.")
            deactivateSaveButton()
        }
    }

    companion object {
        private const val DEACTIVATED = "deactivated"
        private const val DISABLED = "disabled"
    }
}
