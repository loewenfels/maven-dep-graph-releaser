package ch.loewenfels.depgraph.gui.components

import ch.loewenfels.depgraph.gui.getUnderlyingHtmlElement
import kotlinx.html.*
import kotlinx.html.js.onKeyUpFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement

fun DIV.textFieldWithLabel(id: String, label: String, value: String, menu: Menu) {
    textFieldWithLabel(id, label, value, menu, {})
}

fun DIV.textFieldReadOnlyWithLabel(
    id: String,
    label: String,
    value: String,
    menu: Menu,
    inputAct: INPUT.() -> Unit = {}
) {
    textFieldWithLabel(id, label, value, menu, { readonly = true; inputAct() })
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
