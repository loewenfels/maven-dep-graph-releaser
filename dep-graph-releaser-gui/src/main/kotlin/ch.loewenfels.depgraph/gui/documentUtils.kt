package ch.loewenfels.depgraph.gui

import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.browser.document
import kotlin.reflect.KClass

fun elementById(id: String) = elementById(id, HTMLElement::class)

fun <T : HTMLElement> elementById(id: String, klass: KClass<T>): T = elementByIdOrNull(
    id,
    klass
)
    ?: throw IllegalStateException("no element found for id $id (expected type ${klass.js.name})")

fun <T : Element> elementByIdOrNull(id: String, klass: KClass<T>): T? {
    val element = document.getElementById(id) ?: return null
    require(klass.isInstance(element)) {
        "element with $id found but was wrong type.<br/>Expected type ${klass.js.name}<br/>Found $element"
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
    val element = document.getElementById(id) ?: return null
    require(element is HTMLInputElement && element.type == type) {
        "$id was either not an input element or did not have type $type: $element"
    }
    return element as HTMLInputElement
}
