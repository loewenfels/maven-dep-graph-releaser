package ch.loewenfels.depgraph.gui.components

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.ReleaseState
import ch.loewenfels.depgraph.generateEclipsePsf
import ch.loewenfels.depgraph.generateGitCloneCommands
import ch.loewenfels.depgraph.generateListOfDependentsWithoutSubmoduleAndExcluded
import ch.loewenfels.depgraph.gui.*
import ch.loewenfels.depgraph.gui.actions.Downloader
import ch.loewenfels.depgraph.gui.actions.Publisher
import ch.loewenfels.depgraph.gui.actions.Releaser
import ch.loewenfels.depgraph.gui.jobexecution.*
import ch.loewenfels.depgraph.gui.serialization.ModifiableState
import ch.tutteli.kbox.mapWithIndex
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
    private val toolsButton get() = elementById("tools")
    private val settingsButton get() = elementById("settings")
    private val eclipsePsfButton get() = elementById("eclipsePsf")
    private val gitCloneCommandsButton get() = elementById("gitCloneCommands")
    private val listDependentsButton get() = elementById("listDependents")

    private var publisher: Publisher? = null
    private var typeOfRun = TypeOfRun.SIMULATION

    init {
        setUpMenuLayers(
            Triple(toolsButton, "toolbox", TOOLS_INACTIVE_TITLE to "Close the toolbox."),
            Triple(settingsButton, "config", SETTINGS_INACTIVE_TITLE to "Close Settings.")
        )
    }

    private fun setUpMenuLayers(vararg pairs: Triple<HTMLElement, String, Pair<String, String>>) {
        pairs.forEach { (button, id, inactiveAndActiveTitle) ->
            button.addClickEventListenerIfNotDeactivatedNorDisabled {
                //close the others
                pairs.forEach { (_, otherId) ->
                    if (id != otherId) {
                        elementById(otherId).removeClass("active")
                    }
                }

                val layer = elementById(id)
                if (layer.hasClass("active")) {
                    button.title = inactiveAndActiveTitle.first
                } else {
                    button.title = inactiveAndActiveTitle.second
                }
                layer.toggleClass("active")
            }
            elementById("$id:close").addClickEventListener {
                elementById(id).removeClass("active")
            }
        }
    }

    fun disableButtonsDueToNoPublishUrl() {
        val titleButtons =
            "You need to specify &publishJob if you want to use other functionality than Download and Explore Release Order."
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

    fun setVerifiedUser(name: String) {
        userName.innerText = name
        userIcon.innerText = "verified_user"
        userButton.removeClass(DEACTIVATED)
    }

    fun setHalfVerified(defaultJenkinsBaseUrl: String?, remoteJenkinsBaseUrl: String) {
        if (!userButton.hasClass(DEACTIVATED)) {
            userIcon.innerText = "error"
            userButton.addClass("warning")
            showWarning(
                "You are not logged in at $remoteJenkinsBaseUrl.\n" +
                    "You can perform a Dry Run (runs on $defaultJenkinsBaseUrl) but a release involving the remote jenkins will most likely fail.\n\n" +
                    "Go to the log in: $remoteJenkinsBaseUrl/login?from=" + window.location
            )
        }
    }

    fun appendToUserButtonToolTip(url: String, username: String, name: String?) {
        val nameSuffix = if (name != null) " ($name)" else ""
        userButton.title += "\nLogged in as $username$nameSuffix @ $url"
    }

    internal fun initDependencies(
        downloader: Downloader,
        dependencies: Dependencies?,
        modifiableState: ModifiableState
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
        initRunButtons(dependencies, modifiableState)
        activateToolsButton()
        activateSettingsButton()
        initExportButtons(modifiableState)

        val releasePlan = modifiableState.releasePlan
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

    private fun initRunButtons(dependencies: Dependencies?, modifiableState: ModifiableState) {
        if (dependencies != null) {

            activateDryRunButton()
            dryRunButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                typeOfRun = TypeOfRun.DRY_RUN
                triggerRelease(
                    modifiableState.releasePlan,
                    dependencies,
                    dependencies.jenkinsJobExecutor,
                    modifiableState.dryRunExecutionDataFactory
                )
            }
            activateReleaseButton()
            releaseButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                typeOfRun = TypeOfRun.RELEASE
                triggerRelease(
                    modifiableState.releasePlan,
                    dependencies,
                    dependencies.jenkinsJobExecutor,
                    modifiableState.releaseJobExecutionDataFactory
                )
            }
        }

        activateExploreButton()
        val fakeJenkinsBaseUrl = "https://github.com/loewenfels/"
        val nonNullDependencies = dependencies ?: App.createDependencies(
            fakeJenkinsBaseUrl,
            "${fakeJenkinsBaseUrl}dgr-publisher/",
            UsernameAndApiToken("test", "test"),
            modifiableState,
            this
        )!!

        exploreButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            typeOfRun = TypeOfRun.SIMULATION
            publisher = nonNullDependencies.publisher
            triggerRelease(
                modifiableState.releasePlan,
                nonNullDependencies,
                nonNullDependencies.simulatingJobExecutor,
                modifiableState.releaseJobExecutionDataFactory
            ).finally {
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
                    """
                    |Release ended successfully :) you can now close the window.
                    |Use a new pipeline for a new release (also in case you performed a Dry Run).
                    |
                    |Please report a bug at $GITHUB_NEW_ISSUE in case some job failed without us noticing it.
                    |Do not forget to star the repository if you like dep-graph-releaser ;-) $GITHUB_REPO
                    |Last but not least, you might want to visit $LOEWENFELS_URL to get to know the company pushing this project forward.
                    """.trimMargin()
                )
            } else {
                showError(
                    """
                    |Release ended with failure :(
                    |At least one job failed. Check errors, fix them and then you can re-trigger the failed jobs, the pipeline respectively, by clicking on the release button (you might have to delete git tags and remove artifacts if they have already been created).
                    |
                    |Please report a bug at $GITHUB_NEW_ISSUE in case a job failed due to an error in dep-graph-releaser.
                    """.trimMargin()
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
                button.title = "Continue with the $processName process by re-processing previously failed projects."
                button.removeClass(DISABLED)
            }
        }
    }

    private fun initExportButtons(modifiableState: ModifiableState) {
        activateButton(eclipsePsfButton, "Download an eclipse psf-file to import all projects into eclipse.")
        activateButton(gitCloneCommandsButton, "Show git clone commands to clone the involved projects.")
        activateButton(listDependentsButton, "List direct and indirect dependent projects (identifiers) of the root project.")

        eclipsePsfButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            val releasePlan = modifiableState.releasePlan
            val psfContent = generateEclipsePsf(
                releasePlan,
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX)),
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REGEX)),
                releasePlan.getConfig(ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT)
            )
            Downloader.download("customImport.psf", psfContent)
        }

        gitCloneCommandsButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            val releasePlan = modifiableState.releasePlan
            val gitCloneCommands = generateGitCloneCommands(
                releasePlan,
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX)),
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REGEX)),
                releasePlan.getConfig(ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT)
            )
            val title = "Copy the following git clone commands and paste them into a terminal/command prompt"
            showOutput(title, gitCloneCommands)
        }

        listDependentsButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            val releasePlan = modifiableState.releasePlan
            val list = generateListOfDependentsWithoutSubmoduleAndExcluded(
                releasePlan,
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX))
            )
            val title = "The following projects are (indirect) dependents of ${releasePlan.rootProjectId.identifier}"
            showOutput(title, list)
        }
    }

    private fun triggerRelease(
        releasePlan: ReleasePlan,
        dependencies: Dependencies,
        jobExecutor: JobExecutor,
        jobExecutionDataFactory: JobExecutionDataFactory
    ): Promise<*> {
        if (Pipeline.getReleaseState() === ReleaseState.Failed) {
            if (typeOfRun == TypeOfRun.DRY_RUN) {
                turnFailedProjectsIntoReTriggerAndReady(releasePlan)
            } else {
                turnFailedCommandsIntoStateReTrigger(releasePlan)
            }
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

    private fun turnFailedProjectsIntoReTriggerAndReady(releasePlan: ReleasePlan) {
        releasePlan.iterator().forEach { project ->
            if (!project.isSubmodule && project.hasFailedCommandsOrSubmoduleHasFailedCommands(releasePlan)) {
                turnCommandsIntoStateReadyToReTriggerAndReady(releasePlan, project)
            }
        }
    }

    private fun turnCommandsIntoStateReadyToReTriggerAndReady(releasePlan: ReleasePlan, project: Project) {
        project.commands.forEachIndexed { index, _ ->
            val commandState = Pipeline.getCommandState(project.id, index)
            if (commandState === CommandState.Failed) {
                changeToStateReadyToReTrigger(project, index)
            } else if (commandState === CommandState.Succeeded) {
                changeStateToReadyWithoutCheck(project, index)
            }
        }
        releasePlan.getSubmodules(project.id).forEach {
            val submodule = releasePlan.getProject(it)
            turnCommandsIntoStateReadyToReTriggerAndReady(releasePlan, submodule)
        }
    }

    private fun Project.hasFailedCommandsOrSubmoduleHasFailedCommands(releasePlan: ReleasePlan): Boolean {
        return commands.mapWithIndex()
            .any { (index, _) -> Pipeline.getCommandState(id, index) === CommandState.Failed }
            || releasePlan.getSubmodules(id).any {
            releasePlan.getProject(it).hasFailedCommandsOrSubmoduleHasFailedCommands(releasePlan)
        }
    }

    private fun turnFailedCommandsIntoStateReTrigger(releasePlan: ReleasePlan) {
        releasePlan.iterator().forEach { project ->
            project.commands.forEachIndexed { index, _ ->
                val commandState = Pipeline.getCommandState(project.id, index)
                if (commandState === CommandState.Failed) {
                    changeToStateReadyToReTrigger(project, index)
                }
            }
        }
    }

    private fun changeStateToReadyWithoutCheck(project: Project, index: Int) {
        Pipeline.changeStateOfCommand(project, index, CommandState.Ready, Pipeline.STATE_READY) { _, _ ->
            //we don't check transition here, Succeeded to Ready is normally not allowed
            CommandState.Ready
        }
    }

    private fun changeToStateReadyToReTrigger(project: Project, index: Int) {
        Pipeline.changeStateOfCommand(project, index, CommandState.ReadyToReTrigger, Pipeline.STATE_READY_TO_BE_TRIGGER)
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

    private fun activateDryRunButton() = activateButton(
        dryRunButton, "Start a dry run based on this release plan (no commit will be made, no artifact deployed etc.)."
    )

    private fun activateReleaseButton() = activateButton(
        releaseButton, "Start a release based on this release plan."
    )

    private fun activateExploreButton() = activateButton(
        exploreButton, "See in which order the projects are build, actual order may vary due to unequal execution time."
    )

    private fun activateToolsButton() = activateButton(
        toolsButton, TOOLS_INACTIVE_TITLE
    )

    private fun activateSettingsButton() = activateButton(
        settingsButton, SETTINGS_INACTIVE_TITLE
    )

    private fun activateButton(button: HTMLElement, newTitle: String) {
        if (button.isDisabled()) return

        button.removeClass(DEACTIVATED)
        button.title = newTitle
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
                    "Save button should not be activate if no publish job url was specified." +
                        "\nPlease report a bug: $GITHUB_REPO"
                )
            )
        }

        val changed = publisher.applyChanges()
        return if (changed) {
            val publishId = getTextField(Gui.RELEASE_ID_HTML_ID).value
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

        private const val TOOLS_INACTIVE_TITLE = "Open the toolbox to see further available features."
        private const val SETTINGS_INACTIVE_TITLE = "Open Settings."

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
        val simulatingJobExecutor: JobExecutor
    )

    private enum class TypeOfRun {
        SIMULATION,
        DRY_RUN,
        RELEASE
    }
}
