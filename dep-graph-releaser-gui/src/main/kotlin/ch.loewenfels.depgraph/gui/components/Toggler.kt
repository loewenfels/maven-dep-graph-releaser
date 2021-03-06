package ch.loewenfels.depgraph.gui.components

import ch.loewenfels.depgraph.gui.SECOND
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleaseCommand
import ch.loewenfels.depgraph.gui.addChangeEventListener
import ch.loewenfels.depgraph.gui.addClickEventListener
import ch.loewenfels.depgraph.gui.components.Messages.Companion.showInfo
import ch.loewenfels.depgraph.gui.elementById
import ch.loewenfels.depgraph.gui.jobexecution.EventManager
import ch.loewenfels.depgraph.gui.serialization.ModifiableState
import ch.tutteli.kbox.mapWithIndex
import org.w3c.dom.CustomEvent
import org.w3c.dom.CustomEventInit
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.dom.hasClass
import kotlin.js.Promise

class Toggler(private val modifiableState: ModifiableState, private val menu: Menu) {

    init {
        modifiableState.releasePlan.getProjects().forEach { project ->
            registerCommandToggleEvents(project)
            registerReleaseUncheckEventForDependentsAndSubmodules(project)
        }
    }

    private fun registerCommandToggleEvents(project: Project) {
        project.commands.forEachIndexed { index, command ->
            val toggle = Pipeline.getToggle(project, index)

            if (command is ReleaseCommand) {
                toggle.addChangeEventListener { toggleCommand(project, index, EVENT_RELEASE_TOGGLE_UNCHECKED) }
                disallowClickIfNotAllCommandsOrSubmodulesActive(project, toggle)
                val releasePlan = modifiableState.releasePlan
                val projectAndSubmodules = sequenceOf(project) +
                    releasePlan.getSubmodules(project.id).asSequence().map { releasePlan.getProject(it) }

                projectAndSubmodules.forEach {
                    registerForProjectEvent(it, EVENT_TOGGLE_UNCHECKED) { toggle.uncheck() }
                }
            } else {
                toggle.addChangeEventListener { toggleCommand(project, index, EVENT_TOGGLE_UNCHECKED) }
            }

            val slider = getSlider(toggle)
            EventManager.disableUnDisableForProcessStartAndEnd(toggle, slider)
            EventManager.unDisableForProcessContinueAndReset(toggle, slider)
        }
    }

    private fun toggleCommand(project: Project, index: Int, uncheckedEvent: String) {
        val toggle = Pipeline.getToggle(project, index)
        val command = Pipeline.getCommand(project, index).asDynamic()
        val slider = getSlider(toggle)
        if (!toggle.checked) {
            dispatchToggleEvent(project, toggle, uncheckedEvent)
            val previous = command.state as CommandState
            Pipeline.changeStateOfCommand(project, index, CommandState.Deactivated(previous))
            slider.title = "Click to activate command."
        } else {
            val oldState = command.state as CommandState.Deactivated
            Pipeline.changeStateOfCommand(project, index, oldState.previous)
            slider.title = "Click to deactivate command."
        }
        menu.activateSaveButtonAndDeactivateOthers()
    }

    private fun disallowClickIfNotAllCommandsOrSubmodulesActive(project: Project, toggle: HTMLInputElement) {
        toggle.addClickEventListener { e ->
            if (toggle.checked && notAllCommandsOrSubmodulesActive(project, toggle)) {
                // cannot reactivate release command if not all commands are active
                // setting checked again to false
                //toggle.checked = false
                e.preventDefault()
                showInfo(
                    "Cannot reactivate the ReleaseCommand for project ${project.id.identifier} " +
                        "because some commands (of submodules) are deactivated.",
                    4 * SECOND
                )
            }
        }
    }

    private fun notAllCommandsOrSubmodulesActive(project: Project, toggle: HTMLInputElement): Boolean =
        notAllCommandsOrSubmodulesActive(project) { it.id != toggle.id }

    private fun notAllCommandsOrSubmodulesActive(project: Project, predicate: (HTMLInputElement) -> Boolean): Boolean =
        notAllCommandsActive(project, predicate) || notAllSubmodulesActive(project)

    private fun notAllCommandsActive(project: Project, predicate: (HTMLInputElement) -> Boolean): Boolean =
        project.commands.asSequence()
            .mapIndexed { index, _ -> Pipeline.getToggle(project, index) }
            .filter(predicate)
            .any { checkbox -> !checkbox.checked }

    private fun notAllSubmodulesActive(project: Project): Boolean =
        modifiableState.releasePlan.getSubmodules(project.id).any { submoduleId ->
            val submodulesHasCommands = !elementById(project.id.identifier).hasClass("withoutCommands")
            submodulesHasCommands &&
                notAllCommandsOrSubmodulesActive(
                    modifiableState.releasePlan.getProject(submoduleId),
                    //cannot be the same command, hence we do not filter commands at all => thus we use `{ true }`
                    predicate = { true }
                )
        }

    private fun registerReleaseUncheckEventForDependentsAndSubmodules(project: Project) {
        if (!project.isSubmodule) {
            val projectIds = modifiableState.releasePlan.collectDependentsInclDependentsOfAllSubmodules(project.id)

            projectIds.forEach { (projectId, dependentId) ->
                modifiableState.releasePlan.getProject(dependentId).commands
                    .mapWithIndex()
                    .filter { (_, command) ->
                        if (command is ReleaseCommand) {
                            // release command will get deactivated automatically via deactivation dependency update
                            false
                        } else {
                            val state = command.state
                            state is CommandState.Waiting && state.dependencies.contains(projectId)
                        }
                    }
                    .forEach { (index, _) ->
                        registerForProjectEvent(project, EVENT_RELEASE_TOGGLE_UNCHECKED) {
                            Pipeline.getToggle(modifiableState.releasePlan.getProject(dependentId), index).uncheck()
                        }
                    }
            }
        }
    }

    private fun HTMLInputElement.uncheck() = changeChecked(this, false)

    private fun changeChecked(toggle: HTMLInputElement, checked: Boolean) {
        if (toggle.checked == checked) return

        toggle.checked = checked
        Promise.resolve(0).then {
            //used to avoid stack-overflow
            toggle.dispatchEvent(Event("change"))
        }
    }

    private fun dispatchToggleEvent(project: Project, toggle: HTMLInputElement, event: String) {
        projectElement(project).dispatchEvent(CustomEvent(event, CustomEventInit(toggle)))
    }

    private fun registerForProjectEvent(project: Project, event: String, callback: (Event) -> Unit) {
        projectElement(project).addEventListener(event, callback)
    }

    private fun projectElement(project: Project) = elementById(project.id.identifier)

    companion object {
        private const val EVENT_TOGGLE_UNCHECKED = "toggle.unchecked"
        private const val EVENT_RELEASE_TOGGLE_UNCHECKED = "release.toggle.unchecked"

        fun getSlider(toggle: HTMLInputElement) = elementById("${toggle.id}${Pipeline.SLIDER_SUFFIX}")
    }
}
