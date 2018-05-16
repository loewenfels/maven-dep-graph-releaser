package ch.loewenfels.depgraph.gui.components

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.ReleaseState
import ch.loewenfels.depgraph.gui.*
import ch.loewenfels.depgraph.gui.actions.Downloader
import ch.loewenfels.depgraph.gui.actions.Publisher
import ch.loewenfels.depgraph.gui.actions.Releaser
import ch.loewenfels.depgraph.gui.jobexecution.JobExecutionDataFactory
import ch.loewenfels.depgraph.gui.jobexecution.JobExecutor
import ch.loewenfels.depgraph.gui.jobexecution.UsernameToken
import ch.loewenfels.depgraph.gui.serialization.ModifiableJson
import org.w3c.dom.CustomEvent
import org.w3c.dom.CustomEventInit
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.hasClass
import kotlin.dom.removeClass
import kotlin.js.Promise

external fun encodeURIComponent(encodedURI: String): String

class Menu {
    private val userButton get() = elementById("user")
    private val userIcon get() = elementById("user.icon")
    private val userName get() = elementById("user.name")
    private val saveButton get() = elementById("save")
    private val downloadButton get() = elementById("download")
    private val dryRunButton get() = elementById("dryRun")
    private val releaseButton get() = elementById("release")
    private val exploreButton get() = elementById("explore")
    private val settingsButton get() = elementById("settings")

    private var publisher: Publisher? = null
    private var typeOfRun = TypeOfRun.SIMULATION

    init {
        settingsButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            elementById("config").toggleClass("active")
        }
        elementById("config:close").addClickEventListener {
            elementById("config").removeClass("active")
        }
    }

    fun disableButtonsDueToNoPublishUrl() {
        val titleButtons =
            "You need to specify publishJob if you want to use other functionality than Download and Explore Release Order."
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
        userButton.removeClass(DEACTIVATED)
    }


    internal fun initDependencies(
        releasePlan: ReleasePlan,
        downloader: Downloader,
        dependencies: Dependencies?,
        modifiableJson: ModifiableJson
    ) {
        if (dependencies != null) {
            publisher = dependencies.publisher
        }

        window.onbeforeunload = {
            if (!saveButton.hasClass(DEACTIVATED)) {
                "Your changes will be lost, sure you want to leave the page?"
            } else if (Pipeline.getReleaseState() === ReleaseState.InProgress) {
                "You might lose state changes if you navigate away from this page, sure you want to proceed?"
            } else {
                null
            }
        }

        initSaveAndDownloadButton(downloader, dependencies)
        initRunButtons(releasePlan, dependencies, modifiableJson)

        when (releasePlan.state) {
            ReleaseState.Ready -> Unit /* nothing to do */
            ReleaseState.InProgress -> dispatchReleaseStart()
            ReleaseState.Failed, ReleaseState.Succeeded -> {
                dispatchReleaseStart(); dispatchReleaseEnd(
                    releasePlan.state == ReleaseState.Succeeded
                )
            }
        }
    }

    private fun initSaveAndDownloadButton(downloader: Downloader, dependencies: Dependencies?) {
        deactivateSaveButton()
        if (dependencies != null) {
            saveButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                save(dependencies.jenkinsJobExecutor, verbose = true).then {
                    deactivateSaveButton()
                }
            }
        }
        downloadButton.title = "Download the release.json"
        downloadButton.removeClass(DEACTIVATED)
        downloadButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            downloader.download()
        }
    }

    private fun initRunButtons(releasePlan: ReleasePlan, dependencies: Dependencies?, modifiableJson: ModifiableJson) {
        if (dependencies != null) {

            activateDryRunButton()
            dryRunButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                typeOfRun = TypeOfRun.DRY_RUN
                triggerRelease(
                    releasePlan,
                    dependencies,
                    dependencies.jenkinsJobExecutor,
                    dependencies.dryRunExecutionDataFactory)
            }
            activateReleaseButton()
            releaseButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                typeOfRun = TypeOfRun.RELEASE
                triggerRelease(
                    releasePlan,
                    dependencies,
                    dependencies.jenkinsJobExecutor,
                    dependencies.releaseJobExecutionDataFactory
                )
            }
        }

        activateExploreButton()
        val jenkinsUrl = "https://github.com/loewenfels/"
        val nonNullDependencies = dependencies ?: App.createDependencies(
            jenkinsUrl,
            "${jenkinsUrl}dgr-publisher/",
            UsernameToken("test", "test"),
            modifiableJson,
            releasePlan,
            this
        )!!

        exploreButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            typeOfRun = TypeOfRun.SIMULATION
            publisher = nonNullDependencies.publisher
            triggerRelease(
                releasePlan,
                nonNullDependencies,
                nonNullDependencies.simulatingJobExecutor,
                nonNullDependencies.releaseJobExecutionDataFactory
            )
                .finally {
                    //reset to null in case it was not defined previously
                    publisher = dependencies?.publisher
                }
        }
        registerForReleaseStartEvent {
            listOf(dryRunButton, releaseButton, exploreButton).forEach {
                it.addClass(DISABLED)
                it.title = DISABLED_RELEASE_IN_PROGRESS
            }
        }
        registerForReleaseEndEvent { success ->
            if (success) {
                listOf(dryRunButton, releaseButton, exploreButton).forEach {
                    it.title = DISABLED_RELEASE_SUCCESS
                }
                showSuccess(
                    "Release ended successfully :) you can now close the window." +
                        "\nUse a new pipeline for a new release." +
                        "\nPlease report a bug in case some job failed without us noticing it."
                )
            } else {
                showError(
                    "Release ended with failure :(" +
                        "\nAt least one job failed. Check errors, fix them and then you can re-trigger the failed jobs, the pipeline respectively, by clicking on the release button." +
                        "\n(You might have to delete git tags and remove artifacts if they have already been created)."
                )

                val (processName, button, buttonText) = when (typeOfRun) {
                    TypeOfRun.SIMULATION -> Triple(
                        "Explore Release Order",
                        exploreButton,
                        elementById("explore:text")
                    )
                    TypeOfRun.DRY_RUN -> Triple(
                        "Dry Run",
                        dryRunButton,
                        elementById("dryRun:text")
                    )
                    TypeOfRun.RELEASE -> Triple(
                        "Release",
                        releaseButton,
                        elementById("release:text")
                    )
                }
                buttonText.innerText = "Re-trigger failed Jobs"
                button.title = "Continue with the $processName process by re-triggering previously failed jobs."
                button.removeClass(DISABLED)
            }
        }
    }

    private fun triggerRelease(
        releasePlan: ReleasePlan,
        dependencies: Dependencies,
        jobExecutor: JobExecutor,
        jobExecutionDataFactory: JobExecutionDataFactory
    ): Promise<*> {
        if (Pipeline.getReleaseState() === ReleaseState.Failed) {
            turnFailedIntoReTrigger(releasePlan)
        }
        dispatchReleaseStart()
        return dependencies.releaser.release(jobExecutor, jobExecutionDataFactory).then(
            { result ->
                dispatchReleaseEnd(success = result)
            },
            { t ->
                dispatchReleaseEnd(success = false)
                throw t
            }
        )
    }

    private fun turnFailedIntoReTrigger(releasePlan: ReleasePlan) {
        releasePlan.iterator().forEach { project ->
            project.commands.forEachIndexed { index, _ ->
                val commandState =
                    Pipeline.getCommandState(project.id, index)
                if (commandState === CommandState.Failed) {
                    Pipeline.changeStateOfCommand(
                        project,
                        index,
                        CommandState.ReadyToReTrigger,
                        Pipeline.STATE_READY_TO_BE_TRIGGER
                    )
                }
            }
        }
    }

    private fun HTMLElement.addClickEventListenerIfNotDeactivatedNorDisabled(action: () -> Any) {
        addClickEventListener {
            @Suppress("RedundantUnitExpression")
            if (hasClass(DEACTIVATED) || hasClass(DISABLED)) return@addClickEventListener Unit
            action()
        }
    }

    private fun HTMLElement.disable(reason: String) {
        this.addClass(DISABLED)
        this.title = reason
    }

    private fun HTMLElement.isDisabled() = hasClass(DISABLED)

    private fun HTMLElement.deactivate(reason: String) {
        if (saveButton.isDisabled()) return

        this.addClass(DEACTIVATED)
        this.setTitleSaveOld(reason)
    }

    private fun deactivateSaveButton() {
        saveButton.deactivate("Nothing to save, no changes were made")
        listOf(dryRunButton, releaseButton, exploreButton).forEach {
            val oldTitle = it.getOldTitleOrNull()
            if (oldTitle != null) {
                it.title = oldTitle
                it.removeClass(DEACTIVATED)
            }
        }
    }

    fun activateSaveButton() {
        if (saveButton.isDisabled()) return

        saveButton.removeClass(DEACTIVATED)
        saveButton.title = "Publish changed json file and change location"
        val saveFirst = "You need to save your changes first."
        listOf(dryRunButton, releaseButton, exploreButton).forEach {
            it.deactivate(saveFirst)
        }
    }

    private fun activateDryRunButton() {
        if (dryRunButton.isDisabled()) return

        dryRunButton.removeClass(DEACTIVATED)
        dryRunButton.title = "Start a dry run based on this release plan (no commit will be made, no artifact deployed etc.)."
    }


    private fun activateReleaseButton() {
        if (releaseButton.isDisabled()) return

        releaseButton.removeClass(DEACTIVATED)
        releaseButton.title = "Start a release based on this release plan."
    }

    private fun activateExploreButton() {
        if (exploreButton.isDisabled()) return

        exploreButton.removeClass(DEACTIVATED)
        exploreButton.title =
            "See in which order the projects are build, actual order may vary due to unequal execution time."
    }

    /**
     * Applies changes and publishes the new release.json with the help of the [Publisher].
     * @return `true` if publishing was carried out, `false` in case there were not any changes.
     */
    fun save(jobExecutor: JobExecutor, verbose: Boolean): Promise<Boolean> {
        val publisher = publisher
        if (publisher == null) {
            deactivateSaveButton()
            showThrowableAndThrow(
                IllegalStateException(
                    "Save button should not be activate if no publish job url was specified.\nPlease report a bug."
                )
            )
        }

        val changed = publisher.applyChanges()
        return if (changed) {
            val publishId = getTextField(Gui.RELEASE_ID_HTML_ID)
                .value
            val newFileName = "release-$publishId"
            publisher.publish(newFileName, verbose, jobExecutor)
                .then { true }
        } else {
            if (verbose) showInfo("Seems like all changes have been reverted manually. Will not save anything.")
            deactivateSaveButton()
            Promise.resolve(false)
        }
    }

    companion object {
        private const val DEACTIVATED = "deactivated"
        private const val DISABLED = "disabled"

        private const val EVENT_RELEASE_START = "release.start"
        private const val EVENT_RELEASE_END = "release.end"
        private const val DISABLED_RELEASE_IN_PROGRESS = "disabled due to release which is in progress."
        private const val DISABLED_RELEASE_SUCCESS = "Release successful, use a new pipeline for a new release."


        fun registerForReleaseStartEvent(callback: (Event) -> Unit) {
            elementById("menu")
                .addEventListener(EVENT_RELEASE_START, callback)
        }

        fun registerForReleaseEndEvent(callback: (Boolean) -> Unit) {
            elementById("menu")
                .addEventListener(EVENT_RELEASE_END, { e ->
                val customEvent = e as CustomEvent
                val success = customEvent.detail as Boolean
                callback(success)
            })
        }

        private fun dispatchReleaseStart() {
            elementById("menu")
                .dispatchEvent(Event(EVENT_RELEASE_START))
        }

        private fun dispatchReleaseEnd(success: Boolean) {
            elementById("menu")
                .dispatchEvent(CustomEvent(EVENT_RELEASE_END, CustomEventInit(detail = success)))
        }

        fun disableUnDisableForReleaseStartAndEnd(input: HTMLInputElement, titleElement: HTMLElement) {
            registerForReleaseStartEvent {
                input.asDynamic().oldDisabled = input.disabled
                input.disabled = true
                titleElement.setTitleSaveOld(DISABLED_RELEASE_IN_PROGRESS)
            }
            registerForReleaseEndEvent { success ->
                if (success) {
                    titleElement.title = DISABLED_RELEASE_SUCCESS
                } else {
                    input.disabled = input.asDynamic().oldDisabled as Boolean
                    titleElement.title = titleElement.getOldTitle()
                }
            }
        }
    }

    internal class Dependencies(
        val publisher: Publisher,
        val releaser: Releaser,
        val jenkinsJobExecutor: JobExecutor,
        val simulatingJobExecutor: JobExecutor,
        val releaseJobExecutionDataFactory: JobExecutionDataFactory,
        val dryRunExecutionDataFactory: JobExecutionDataFactory
    )

    private enum class TypeOfRun{
        SIMULATION,
        DRY_RUN,
        RELEASE
    }
}
