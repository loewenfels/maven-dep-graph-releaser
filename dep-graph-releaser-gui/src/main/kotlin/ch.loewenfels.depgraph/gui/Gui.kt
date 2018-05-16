package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onKeyUpFunction
import org.w3c.dom.*
import kotlin.browser.document

class Gui(private val releasePlan: ReleasePlan, private val menu: Menu) {
    private val pipeline = Pipeline(releasePlan, menu)

    fun load() {
        val rootProjectId = releasePlan.rootProjectId
        val htmlTitle = (rootProjectId as? MavenProjectId)?.artifactId ?: rootProjectId.identifier
        document.title = "Release $htmlTitle"
        setUpMessages(releasePlan.warnings, "warnings", { showWarning(it) })
        setUpMessages(releasePlan.infos, "infos", { showInfo(it) })
        setUpConfig(releasePlan)
        pipeline.setUp()

        //TODO we should check if releasePlant.state is inProgress. In such a case it might be that command states
        // have changed already and we need to update the state let's say the browser crashes during release and we
        // have already triggered a job and know it is queued in this case we should check if it is no longer queued
        // but already started etc.
        //TODO also for state failed, might be that it failed because maxWaitingTime was over
    }

    private fun setUpMessages(messages: List<String>, id: String, action: (String) -> Unit) {
        if (messages.isNotEmpty()) {
            val minimized = elementById("${id}Minimized")
            minimized.style.display = "block"
            minimized.addEventListener("click", {
                minimized.style.display = "none"
                messages.forEach(action)
            })
        }
        val messagesDiv = elementById("messages")
        elementById(HIDE_MESSAGES_HTML_ID).addClickEventListener {
            document.querySelectorAll("#messages > div")
                .asList()
                .forEach { messagesDiv.removeChild(it) }
        }
    }

    private fun setUpConfig(releasePlan: ReleasePlan) {
        //TODO add description for each property
        elementById("config").append {
            div {
                textFieldWithLabel(RELEASE_ID_HTML_ID, "ReleaseId", releasePlan.releaseId, menu)

                val config = releasePlan.config
                listOf(
                    ConfigKey.COMMIT_PREFIX,
                    ConfigKey.UPDATE_DEPENDENCY_JOB,
                    ConfigKey.REMOTE_REGEX,
                    ConfigKey.REMOTE_JOB,
                    ConfigKey.DRY_RUN_JOB,
                    ConfigKey.REGEX_PARAMS
                ).forEach { key ->
                    textFieldWithLabel("config-${key.asString()}", key.asString(), config[key] ?: "", menu)
                }
                val key = ConfigKey.JOB_MAPPING
                textAreaWithLabel("config-${key.asString()}", key.asString(), config[key]?.replace("|", "\n") ?: "", menu)
            }
        }
    }

    companion object {
        const val RELEASE_ID_HTML_ID = "releaseId"
        const val HIDE_MESSAGES_HTML_ID = "hideMessages"

        fun DIV.textFieldWithLabel(id: String, label: String, value: String, menu: Menu) {
            textFieldWithLabel(id, label, value, menu, {})
        }


        fun DIV.textFieldWithLabel(id: String, label: String, value: String, menu: Menu, inputAct: INPUT.() -> Unit) {
            div {
                label("fields") {
                    htmlFor = id
                    +label
                }
                textInput {
                    this.id = id
                    this.value = value
                    inputAct()
                    onKeyUpFunction = { menu.activateSaveButton() }
                    val input = getUnderlyingHtmlElement() as HTMLInputElement
                    Menu.disableUnDisableForReleaseStartAndEnd(input, input)
                }
            }
        }

        fun DIV.textAreaWithLabel(id: String, label: String, value: String, menu: Menu) {
            div {
                label("fields") {
                    htmlFor = id
                    +label
                }
                textArea {
                    this.id = id
                    +value
                    onKeyUpFunction = { menu.activateSaveButton() }
                    val htmlTextAreaElement = getUnderlyingHtmlElement() as HTMLTextAreaElement
                    //for what disableUnDisableForReleaseStartAndEnd needs, title and disabled, it is ok to make the unsafe cast
                    //TODO change in case https://github.com/Kotlin/kotlinx.html/issues/87 is implemented
                    val input = htmlTextAreaElement.unsafeCast<HTMLInputElement>()
                    Menu.disableUnDisableForReleaseStartAndEnd(input, htmlTextAreaElement)
                }
            }
        }
    }
}
