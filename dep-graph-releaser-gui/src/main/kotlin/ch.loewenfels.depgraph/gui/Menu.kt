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
    private val buildButton get() = elementById("build")
    private lateinit var body: String


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
        listOf(saveButton, dryRunButton, buildButton).forEach { it.disable(titleButtons) }
    }

    fun setVerifiedUser(username: String, name: String) {
        userName.innerText = name
        userIcon.innerText = "verified_user"
        userButton.title = "Logged in as $username"
        userButton.removeClass(Menu.DEACTIVATED)
    }


    fun initDownloaderAndPublisher(downloader: Downloader, publisher: Publisher?, body: String) {
        this.body = body

        window.onbeforeunload = {
            if (!saveButton.hasClass(DEACTIVATED)) {
                "Your changes will be lost, sure you want to leave the page?"
            } else {
                null
            }
        }

        initSaveAndDownloadButton(downloader, publisher)
        initDryRunAndBuildButton()
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
            download(downloader)
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

    private fun save(publisher: Publisher?) {
        val (changed, newJson) = Serializer.createReleasePlanJsonWithChanges(body)
        if (changed) {
            if (publisher != null) {
                val newFileName = "release-${generateUniqueId()}"
                publisher.publish(newJson, newFileName)
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

    private fun download(downloader: Downloader) {
        val (_, json) = Serializer.createReleasePlanJsonWithChanges(body)
        downloader.download(json)
    }

    companion object {
        private const val DEACTIVATED = "deactivated"
        private const val DISABLED = "disabled"
    }
}
