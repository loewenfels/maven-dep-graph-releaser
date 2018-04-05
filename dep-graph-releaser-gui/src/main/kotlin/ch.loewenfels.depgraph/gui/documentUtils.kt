package ch.loewenfels.depgraph.gui

import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
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
