package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import ch.loewenfels.depgraph.gui.actions.Downloader
import ch.loewenfels.depgraph.gui.actions.Publisher
import ch.loewenfels.depgraph.gui.actions.Releaser
import ch.loewenfels.depgraph.gui.components.Loader
import ch.loewenfels.depgraph.gui.components.Menu
import ch.loewenfels.depgraph.gui.jobexecution.*
import ch.loewenfels.depgraph.gui.serialization.ModifiableState
import ch.loewenfels.depgraph.gui.serialization.ProjectJson
import ch.loewenfels.depgraph.gui.serialization.ReleasePlanJson
import ch.loewenfels.depgraph.gui.serialization.deserializeProjectId
import ch.loewenfels.depgraph.parseRemoteRegex
import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.*

class App {
    private val publishJobUrl: String?
    private val defaultJenkinsBaseUrl: String?
    private val menu: Menu

    init {
        Loader.updateLoaderToLoadApiToken()

        val jsonUrl = determineJsonUrlOrThrow()
        publishJobUrl = determinePublishJob()
        defaultJenkinsBaseUrl = publishJobUrl?.substringBefore("/job/")
        menu = Menu(UsernameTokenRegistry, defaultJenkinsBaseUrl)
        start(jsonUrl)
    }

    private fun determinePublishJob(): String? {
        return if (window.location.hash.contains(PUBLISH_JOB)) {
            getJobUrl(window.location.hash.substringAfter(PUBLISH_JOB))
        } else {
            null
        }
    }

    private fun getJobUrl(possiblyRelativePublishJobUrl: String): String {
        require(!possiblyRelativePublishJobUrl.contains("://") || possiblyRelativePublishJobUrl.startsWith("http")) {
            "The publish job URL does not start with http but contains ://"
        }

        val prefix = window.location.protocol + "//" + window.location.hostname + "/"
        val tmpUrl = if (possiblyRelativePublishJobUrl.contains("://")) {
            possiblyRelativePublishJobUrl
        } else {
            prefix + possiblyRelativePublishJobUrl
        }
        return if (tmpUrl.endsWith("/")) tmpUrl else "$tmpUrl/"
    }

    private fun start(jsonUrl: String) {
        retrieveUserAndApiToken().then { usernameAndApiToken ->
            display("gui", "block")
            Loader.updateToLoadingJson()

            loadJsonAndCheckStatus(jsonUrl, usernameAndApiToken)
                .then { (_, body) ->
                    val modifiableState = ModifiableState(defaultJenkinsBaseUrl, body)
                    val releasePlan = modifiableState.releasePlan
                    val promise = if (usernameAndApiToken != null) {
                        Loader.updateToLoadOtherTokens()
                        loadOtherApiTokens(releasePlan)
                    } else {
                        Promise.resolve(Unit)
                    }
                    promise.then { modifiableState }
                }.then { modifiableState ->
                    val promise = if (modifiableState.releasePlan.state == ReleaseState.IN_PROGRESS) {
                        Loader.updateToRecoverOngoingProcess()
                        recoverInProgress(modifiableState)
                    } else {
                        Promise.resolve(modifiableState)
                    }
                    promise
                }.then { modifiableState ->
                    Loader.updateToLoadPipeline()
                    Gui(modifiableState, menu)
                    val dependencies = createDependencies(
                        defaultJenkinsBaseUrl, publishJobUrl, modifiableState, menu
                    )
                    menu.initDependencies(Downloader(modifiableState), dependencies, modifiableState)
                    switchLoaderWithPipeline()
                }
                .catch {
                    showThrowableAndThrow(it)
                }
        }
    }

    private fun loadOtherApiTokens(releasePlan: ReleasePlan): Promise<*> {
        val remoteRegex = parseRemoteRegex(releasePlan)
        val mutableList = ArrayList<Promise<*>>(remoteRegex.size)

        remoteRegex.forEach { (_, remoteJenkinsBaseUrl) ->
            val promise = if (isUrlAndNotYetRegistered(remoteJenkinsBaseUrl)) {
                UsernameTokenRegistry.register(remoteJenkinsBaseUrl).then { pair ->
                    updateUserToolTip(remoteJenkinsBaseUrl, pair)
                    if (pair == null) {
                        menu.setHalfVerified(defaultJenkinsBaseUrl, remoteJenkinsBaseUrl)
                    }
                }
            } else {
                Promise.resolve(Unit)
            }
            mutableList.add(promise)
        }
        return Promise.all(mutableList.toTypedArray())
    }

    private fun isUrlAndNotYetRegistered(remoteJenkinsBaseUrl: String) =
        remoteJenkinsBaseUrl.startsWith("http") && UsernameTokenRegistry.forHost(remoteJenkinsBaseUrl) == null

    private fun retrieveUserAndApiToken(): Promise<UsernameAndApiToken?> {
        return if (defaultJenkinsBaseUrl == null) {
            menu.disableButtonsDueToNoPublishUrl()
            Promise.resolve(null as UsernameAndApiToken?)
        } else {
            UsernameTokenRegistry.register(defaultJenkinsBaseUrl).then { pair ->
                if (pair == null) {
                    val info = "You need to log in if you want to use other functionality than Download."
                    menu.disableButtonsDueToNoAuth(info, "$info\n$defaultJenkinsBaseUrl/login?from=" + window.location)
                    null
                } else {
                    val (name, usernameToken) = pair
                    menu.setVerifiedUser(name)
                    updateUserToolTip(defaultJenkinsBaseUrl, pair)
                    usernameToken
                }
            }
        }
    }

    private fun updateUserToolTip(url: String, pair: Pair<String, UsernameAndApiToken>?) {
        menu.appendToUserButtonToolTip(url, pair?.second?.username ?: "Anonymous", pair?.first)
    }


    private fun recoverInProgress(modifiableState: ModifiableState): Promise<ModifiableState> {
        if (defaultJenkinsBaseUrl == null) {
            showInfo(
                "You have opened a pipeline which is in state ${ReleaseState.IN_PROGRESS.name}.\n" +
                    "Yet, since you have not provided a $PUBLISH_JOB in the URL we cannot recover the ongoing process."
            )
            return Promise.resolve(modifiableState)
        }

        return showDialog(
            """
            |You have opened a pipeline which is in state ${ReleaseState.IN_PROGRESS.name}.
            |Are you the release manager and would like to recover the ongoing process?
            |
            |Extra information: By clicking 'Yes' the dep-graph-releaser will check if the current state of the individual commands is still appropriate and update if necessary. Furthermore, it will resume the process meaning it will trigger dependent jobs if a job finishes. Or in other words, it will almost look like you have never left the page.
            |
            |Do not click 'Yes' (but 'No') if you (or some else) have started the release process in another tab/browser since otherwise dependent jobs will be triggered multiple times.
            """.trimMargin()
        ).then { isReleaseManager ->
            if (!isReleaseManager) {
                showInfo(
                    "We do not yet support tracking of a release process at the moment. Which means, what you see above is only a state of the process but the process as such has likely progressed already." +
                        "\nPlease open a feature request $GITHUB_NEW_ISSUE if you have the need of tracking a release (which runs in another tab/browser)."
                )
                return@then Promise.resolve(modifiableState)
            }
            recoverCommandStates(modifiableState, defaultJenkinsBaseUrl)
        }.then { it }
    }

    private fun recoverCommandStates(
        modifiableState: ModifiableState,
        jenkinsBaseUrl: String
    ): Promise<ModifiableState> {
        val releasePlanJson = JSON.parse<ReleasePlanJson>(modifiableState.json)
        val promises = modifiableState.releasePlan.iterator().asSequence().map { project ->
            val lazyProjectJson by lazy {
                releasePlanJson.projects.single { deserializeProjectId(it.id) == project.id }
            }
            val promises = project.commands.mapIndexed { index, command ->
                when (command.state) {
                //TODO we need also to check if a job is queueing or already finished if the state is Ready.
                // It could be that we trigger a job and then the browser crashed (or the user closed the page)
                // before we had a chance to publish the new state => We could introduce a state Triggered but
                // this would mean we need one more publish per job which is bad. This brings me to another idea,
                // we could get rid of the save after state queueing if we implement recovery from state ready.
                // Nah... then we wouldn't save anything anymore which is bad as well (we have to save from time
                // to time :D). But I think there is potential here to reduce the number of publishes per pipeline.
                    CommandState.Ready -> TODO("Not yet supported")
                    CommandState.Queueing -> recoverStateQueueing(
                        modifiableState, jenkinsBaseUrl, project, command, lazyProjectJson, index
                    )
                    CommandState.InProgress -> recoverStateTo(lazyProjectJson, index, CommandStateJson.State.RE_POLLING)
                    else -> Promise.resolve(Unit)
                }
            }
            Promise.all(promises.toTypedArray())
        }
        return Promise.all(promises.toList().toTypedArray()).then {
            ModifiableState(modifiableState, JSON.stringify(releasePlanJson))
        }
    }

    private fun recoverStateQueueing(
        modifiableState: ModifiableState,
        jenkinsBaseUrl: String,
        project: Project,
        command: Command,
        lazyProjectJson: ProjectJson,
        index: Int
    ): Promise<*> {
        return if (command is M2ReleaseCommand) {
            val usernameAndApiToken = UsernameTokenRegistry.forHostOrThrow(jenkinsBaseUrl)
            issueCrumb(jenkinsBaseUrl, usernameAndApiToken).then { authData ->
                val jobExecutionData = recoverJobExecutionData(modifiableState, project, command)
                val buildUrl = command.buildUrl
                extractBuildNumber(buildUrl, authData, jobExecutionData).then { buildNumber ->
                    lazyProjectJson.commands[index].p.asDynamic().buildUrl = jobExecutionData.jobBaseUrl + buildNumber
                    recoverStateTo(lazyProjectJson, index, CommandStateJson.State.RE_POLLING)
                }.catch {
                    recoverStateTo(lazyProjectJson, index, CommandStateJson.State.FAILED)
                }
            }
        } else {
            throw UnsupportedOperationException(
                "We do not know how to recover a command of type ${command::class.simpleName}." +
                    "\nCommand: $command"
            )
        }
    }

    private fun recoverJobExecutionData(
        modifiableState: ModifiableState,
        project: Project,
        command: Command
    ): JobExecutionData {
        val jobExecutionDataFactory = when (modifiableState.releasePlan.typeOfRun) {
            TypeOfRun.DRY_RUN -> modifiableState.dryRunExecutionDataFactory
            TypeOfRun.RELEASE, TypeOfRun.SIMULATION -> modifiableState.releaseJobExecutionDataFactory
        }
        return jobExecutionDataFactory.create(project, command)
    }

    private fun recoverStateTo(lazyProjectJson: ProjectJson, index: Int, state: CommandStateJson.State): Promise<*> {
        lazyProjectJson.commands[index].p.state.asDynamic().state = state.name
        return Promise.resolve(Unit)
    }

    private fun switchLoaderWithPipeline() {
        display("loader", "none")
        display("pipeline", "table")
    }


    companion object {
        const val PUBLISH_JOB = "&publishJob="

        fun determineJsonUrlOrThrow(): String {
            return determineJsonUrl() ?: showThrowableAndThrow(
                IllegalStateException(
                    "You need to specify a release.json." +
                        "\nAppend the path with preceding # to the url, e.g., ${window.location}#release.json"
                )
            )
        }


        fun determineJsonUrl(): String? {
            return if (window.location.hash != "") {
                window.location.hash.substring(1).substringBefore("&")
            } else {
                null
            }
        }

        fun loadJsonAndCheckStatus(
            jsonUrl: String,
            usernameAndApiToken: UsernameAndApiToken?
        ): Promise<Pair<Response, String>> {
            return loadJson(jsonUrl, usernameAndApiToken)
                .then(::checkStatusOk)
                .catch<Pair<Response, String>> {
                    throw Error("Could not load json from url $jsonUrl.", it)
                }
        }

        private fun loadJson(jsonUrl: String, usernameAndApiToken: UsernameAndApiToken?): Promise<Response> {
            val init = createFetchInitWithCredentials()
            val headers = js("({})")
            // if &publishJob is not specified, then we don't have usernameAndApiToken but we can still
            // load the json and display it as pipeline
            if (usernameAndApiToken != null) {
                addAuthentication(headers, usernameAndApiToken)
            }
            init.headers = headers
            return window.fetch(jsonUrl, init)
        }

        internal fun createDependencies(
            defaultJenkinsBaseUrl: String?,
            publishJobUrl: String?,
            modifiableState: ModifiableState,
            menu: Menu
        ): Menu.Dependencies? {
            return if (publishJobUrl != null && defaultJenkinsBaseUrl != null) {
                val publisher = Publisher(publishJobUrl, modifiableState)
                val releaser = Releaser(defaultJenkinsBaseUrl, modifiableState, menu)

                val jenkinsJobExecutor = JenkinsJobExecutor(UsernameTokenRegistry)
                val simulatingJobExecutor = SimulatingJobExecutor()
                Menu.Dependencies(
                    publisher,
                    releaser,
                    jenkinsJobExecutor,
                    simulatingJobExecutor
                )
            } else {
                null
            }
        }
    }
}
