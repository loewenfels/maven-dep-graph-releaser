package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.gui.actions.Downloader
import ch.loewenfels.depgraph.gui.actions.Publisher
import ch.loewenfels.depgraph.gui.actions.Releaser
import ch.loewenfels.depgraph.gui.components.Menu
import ch.loewenfels.depgraph.gui.jobexecution.*
import ch.loewenfels.depgraph.gui.serialization.ModifiableState
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
            switchLoader("loaderApiToken", "loaderJson")

            loadJsonAndCheckStatus(jsonUrl, usernameAndApiToken)
                .then { (_, body) ->
                    switchLoader("loaderJson", "loaderPipeline")
                    val modifiableState = ModifiableState(defaultJenkinsBaseUrl, body)
                    val releasePlan = modifiableState.releasePlan
                    val promise = if (usernameAndApiToken != null) {
                        loadOtherApiTokens(releasePlan)
                    } else {
                        Promise.resolve(Unit)
                    }
                    promise.then {
                        val dependencies = createDependencies(
                            defaultJenkinsBaseUrl, publishJobUrl, modifiableState, menu
                        )
                        menu.initDependencies(Downloader(modifiableState), dependencies, modifiableState)
                        Gui(modifiableState, menu)
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
