package ch.loewenfels.depgraph.gui

import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.browser.document

fun elementById(id: String): HTMLElement = elementById<HTMLElement>(id)

inline fun <reified T : HTMLElement> elementById(id: String): T = elementByIdOrNull(id)
    ?: throw IllegalStateException("no element found for id $id (expected type ${T::class.js.name})")

inline fun <reified T : HTMLElement> elementByIdOrNull(id: String): T? {
    val element = document.getElementById(id) ?: return null
    require(element is T) {
        "element with $id found but was wrong type.<br/>Expected type ${T::class.js.name}<br/>Found $element"
    }
    return element.unsafeCast<T>()
}

fun display(id: String, what: String) {
    elementById(id).style.display = what
}

fun getCheckbox(id: String): HTMLInputElement
    = getCheckboxOrNull(id) ?: throw IllegalStateException("no checkbox found for id $id")

fun getCheckboxOrNull(id: String) = getInputElementOrNull(id, "checkbox")

fun getTextField(id: String): HTMLInputElement
    = getTextFieldOrNull(id) ?: throw IllegalStateException("no text field found for id $id")

fun getTextFieldOrNull(id: String) = getInputElementOrNull(id, "text")

fun getInputElementOrNull(id: String, type: String): HTMLInputElement? {
    val element = elementByIdOrNull<HTMLInputElement>(id) ?: return null
    require(element.type == type) {
        "$id was either not an input element or did not have type $type: $element"
    }
    return element
}
