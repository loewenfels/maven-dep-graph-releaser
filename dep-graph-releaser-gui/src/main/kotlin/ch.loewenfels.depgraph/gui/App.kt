package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.gui.actions.Downloader
import ch.loewenfels.depgraph.gui.actions.Publisher
import ch.loewenfels.depgraph.gui.actions.Releaser
import ch.loewenfels.depgraph.gui.components.Menu
import ch.loewenfels.depgraph.gui.jobexecution.*
import ch.loewenfels.depgraph.gui.serialization.ModifiableJson
import ch.loewenfels.depgraph.gui.serialization.deserialize
import ch.loewenfels.depgraph.parseRemoteRegex
import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.js.Promise

class App {
    private val publishJobUrl: String?
    private val defaultJenkinsBaseUrl: String?
    private val menu: Menu

    init {
        switchLoader("loaderJs", "loaderApiToken")

        val jsonUrl = determineJsonUrl()
        publishJobUrl = determinePublishJob()
        defaultJenkinsBaseUrl = publishJobUrl?.substringBefore("/job/")
        menu = Menu()
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


    private fun determineJsonUrl(): String {
        return if (window.location.hash != "") {
            window.location.hash.substring(1).substringBefore("&")
        } else {
            showThrowableAndThrow(
                IllegalStateException(
                    "You need to specify a release.json." +
                        "\nAppend the path with preceding # to the url, e.g., ${window.location}#release.json"
                )
            )
        }
    }

    private fun start(jsonUrl: String) {
        retrieveUserAndApiToken().then { usernameToken ->
            display("gui", "block")
            switchLoader("loaderApiToken", "loaderJson")

            loadJson(jsonUrl, usernameToken)
                .then(::checkStatusOk)
                .catch {
                    throw Error("Could not load json.", it)
                }.then { body: String ->
                    switchLoader("loaderJson", "loaderPipeline")
                    val modifiableJson = ModifiableJson(body)
                    val releasePlan = deserialize(body)
                    loadOtherApiTokens(releasePlan).then {
                        val dependencies = createDependencies(
                            defaultJenkinsBaseUrl, publishJobUrl, usernameToken, modifiableJson, releasePlan, menu
                        )
                        menu.initDependencies(releasePlan, Downloader(modifiableJson), dependencies, modifiableJson)
                        Gui(releasePlan, menu)
                        switchLoaderPipelineWithPipeline()
                    }
                }.catch {
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
                        menu.setHalfVerified()
                        showWarning("You are not logged in at $remoteJenkinsBaseUrl.\n" +
                            "You can perform a Dry Run (runs on $defaultJenkinsBaseUrl) but a release involving the remote jenkins will most likely fail.\n\n" +
                            "Go to the log in: $remoteJenkinsBaseUrl/login?from=" + window.location
                        )
                    }
                }
            } else {
                Promise.resolve(Unit)
            }
            mutableList.add(promise)
        }
        return Promise.all(mutableList.toTypedArray())
    }

    private fun isUrlAndNotYetRegistered(remoteJenkinsBaseUrl: String)
        = remoteJenkinsBaseUrl.startsWith("http") && UsernameTokenRegistry.forHost(remoteJenkinsBaseUrl) == null

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

    private fun loadJson(jsonUrl: String, usernameAndApiToken: UsernameAndApiToken?): Promise<Response> {
        val init = createFetchInitWithCredentials()
        val headers = js("({})")
        // not necessary if we deal with jenkins but e.g. localhost
        if (usernameAndApiToken != null) {
            addAuthentication(headers, usernameAndApiToken)
        }
        init.headers = headers
        return window.fetch(jsonUrl, init)
    }

    private fun switchLoaderPipelineWithPipeline() {
        display("loaderPipeline", "none")
        display("pipeline", "table")
    }

    private fun switchLoader(firstLoader: String, secondLoader: String) {
        display(firstLoader, "none")
        display(secondLoader, "block")
    }

    companion object {
        const val PUBLISH_JOB = "&publishJob="

        internal fun createDependencies(
            defaultJenkinsBaseUrl: String?,
            publishJobUrl: String?,
            usernameAndApiToken: UsernameAndApiToken?,
            modifiableJson: ModifiableJson,
            releasePlan: ReleasePlan,
            menu: Menu
        ): Menu.Dependencies? {
            return if (publishJobUrl != null && defaultJenkinsBaseUrl != null && usernameAndApiToken != null) {
                val publisher = Publisher(publishJobUrl, modifiableJson)
                val releaser = Releaser(defaultJenkinsBaseUrl, modifiableJson, menu)

                val jenkinsJobExecutor = JenkinsJobExecutor(UsernameTokenRegistry)
                val simulatingJobExecutor = SimulatingJobExecutor()
                val releaseJobExecutionDataFactory = ReleaseJobExecutionDataFactory(defaultJenkinsBaseUrl, releasePlan)
                val dryRunJobExecutionDataFactory = DryRunJobExecutionDataFactory(defaultJenkinsBaseUrl, releasePlan)
                Menu.Dependencies(
                    publisher,
                    releaser,
                    jenkinsJobExecutor,
                    simulatingJobExecutor,
                    releaseJobExecutionDataFactory,
                    dryRunJobExecutionDataFactory
                )
            } else {
                null
            }
        }
    }
}
