package ch.loewenfels.depgraph.gui.components

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleaseCommand
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.gui.*
import ch.tutteli.kbox.forEachIn
import ch.tutteli.kbox.mapWithIndex
import kotlinx.html.*
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.removeClass
import kotlin.reflect.KClass

class ContextMenu(private val releasePlan: ReleasePlan, private val menu: Menu) {

    fun createContextMenu(div: DIV, idPrefix: String, project: Project, index: Int) {
        div.div("contextMenu") {
            id = "$idPrefix$CONTEXT_MENU_SUFFIX"
            contextMenuEntry(idPrefix,
                CONTEXT_MENU_DEACTIVATED, CommandState.Deactivated::class) {
                transitionToDeactivatedIfOk(project, index)
            }
            contextMenuEntry(idPrefix,
                CONTEXT_MENU_SUCCEEDED, CommandState.Succeeded::class) {
                transitionToSucceededIfOk(project, index)
            }
        }
    }

    private fun DIV.contextMenuEntry(
        idPrefix: String,
        cssClass: String,
        commandClass: KClass<out CommandState>,
        action: (Event) -> Unit
    ) {
        div(cssClass) {
            id = "$idPrefix$cssClass"
            title = "Forcibly sets the state of this command to ${commandClass.simpleName}, to be used with care."
            i("material-icons") { span() }
            span {
                +"Set Command to ${commandClass.simpleName}"
            }
            getUnderlyingHtmlElement().addClickEventListener(action = action)
        }
    }

    private fun transitionToDeactivatedIfOk(project: Project, index: Int) {
        val commandState = Pipeline.getCommandState(project.id, index)
        if (isNotInStateToDeactivate(commandState)) return


        Pipeline.getToggle(project, index).click()
    }

    private fun isNotInStateToDeactivate(commandState: CommandState): Boolean {
        return commandState is CommandState.Deactivated ||
            commandState === CommandState.Succeeded ||
            commandState === CommandState.Disabled
    }

    private fun transitionToSucceededIfOk(project: Project, index: Int) {
        if (project.commands[index] is ReleaseCommand) {
            if (notAllOtherCommandsSucceeded(project, index)) {
                val succeeded = CommandState.Succeeded::class.simpleName
                showDialog(
                    "You cannot set this command to the state $succeeded because not all other commands of this project have $succeeded yet." +
                        "\n\n" +
                        "Do you want to set all other commands forcibly to $succeeded as well?"
                ).then { setAllToSucceeded ->
                    if (setAllToSucceeded) {
                        transitionAllCommandsToSucceeded(project)
                        menu.activateSaveButton()
                    }
                }
                return
            }
        }
        transitionToSucceeded(project, index)
        menu.activateSaveButton()
    }

    private fun transitionAllCommandsToSucceeded(project: Project) {
        project.commands.forEachIndexed { index, _ ->
            transitionToSucceeded(project, index)
        }
        releasePlan.getSubmodules(project.id).forEach {
            transitionAllCommandsToSucceeded(releasePlan.getProject(it))
        }
    }

    private fun transitionToSucceeded(project: Project, index: Int) {
        Pipeline.changeStateOfCommand(
            project,
            index,
            CommandState.Succeeded,
            Pipeline.stateToTitle(CommandState.Succeeded)
        ) { _, _ ->
            //we don't check transition here, the user has to know what she does (at least for now)
            CommandState.Succeeded
        }
    }

    private fun notAllOtherCommandsSucceeded(project: Project, index: Int?): Boolean {
        return project.commands.asSequence()
            .mapWithIndex()
            .any { (i, _) ->
                (index == null || i != index) && Pipeline.getCommandState(project.id, i) !== CommandState.Succeeded
            }
            || releasePlan.getSubmodules(project.id)
            .any { notAllOtherCommandsSucceeded(releasePlan.getProject(it), null) }
    }

    fun setUpCommandsOnContextMenu() {
        val toggleLabels = document.querySelectorAll(".command > .fields > .toggle")
            .asList()
            .map { label ->
                val toggle = label.firstChild as HTMLInputElement
                label to toggle.id.substringBefore(Pipeline.DEACTIVATE_SUFFIX)
            }
        val stateIcons = document.querySelectorAll(".state")
            .asList()
            .map { aNode ->
                val a = aNode as HTMLAnchorElement
                a to a.id.substringBefore(Pipeline.STATE_SUFFIX)
            }
        forEachIn(toggleLabels, stateIcons) { (element, idPrefix) ->
            element.addEventListener("contextmenu", { event ->
                hideAllContextMenus()
                disableContextEntriesIfNecessary(idPrefix)
                val contextMenu =
                    elementById("$idPrefix$CONTEXT_MENU_SUFFIX")
                moveContextMenuPosition(event as MouseEvent, contextMenu)
                contextMenu.style.visibility = "visible"
                window.addEventListener("click", { hideAllContextMenus() }, js("({once: true})"))
                event.preventDefault()
                event.stopPropagation()
            })
        }
        window.addEventListener("contextmenu", { hideAllContextMenus() })
    }

    private fun disableContextEntriesIfNecessary(idPrefix: String) {
        val commandState = Pipeline.getCommandState(idPrefix)
        disableOrEnableContextMenuEntry("$idPrefix$CONTEXT_MENU_DEACTIVATED", isNotInStateToDeactivate(commandState))
        disableOrEnableContextMenuEntry("$idPrefix$CONTEXT_MENU_SUCCEEDED", commandState === CommandState.Succeeded)
    }

    private fun disableOrEnableContextMenuEntry(id: String, disable: Boolean) {
        val entry = elementById(id)
        if (disable) {
            entry.setTitleSaveOld("Cannot apply this action.")
            entry.addClass("disabled")
        } else {
            val title = entry.getOldTitleOrNull()
            if (title != null) {
                entry.title = title
            }
            entry.removeClass("disabled")
        }
    }

    private fun hideAllContextMenus() {
        document.querySelectorAll(".contextMenu").asList().forEach {
            (it as HTMLElement).style.visibility = "hidden"
        }
    }

    private fun moveContextMenuPosition(event: MouseEvent, contextMenu: HTMLElement) {
        val menuWidth = contextMenu.offsetWidth
        val menuHeight = contextMenu.offsetHeight
        val mouseX = event.pageX
        val mouseY = event.pageY

        val x = if (mouseX + menuWidth > document.body!!.clientWidth + window.scrollX) {
            mouseX - menuWidth
        } else {
            mouseX
        }
        val y = if (mouseY + menuHeight > document.body!!.clientHeight + window.scrollY) {
            mouseY - menuHeight
        } else {
            mouseY
        }
        contextMenu.style.left = "${x}px"
        contextMenu.style.top = "${y}px"
    }

    companion object {
        const val CONTEXT_MENU_SUFFIX = ":contextMenu"
        const val CONTEXT_MENU_DEACTIVATED = "deactivated"
        const val CONTEXT_MENU_SUCCEEDED = "succeeded"
    }
}
