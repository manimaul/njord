package io.madrona.njord.js

import org.w3c.dom.Element

// https://getbootstrap.com/docs/5.3/components/modal/#passing-options
@JsModule("bootstrap")
@JsName("bootstrap")
@JsNonModule
external class Bootstrap {
    class Modal(name: String) {
        fun show()
        fun hide()
        companion object {
            fun getOrCreateInstance(name: Element): Modal?
        }
    }
    class Collapse {
        fun toggle()
        companion object {
            fun getOrCreateInstance(name: Element): Collapse?
            fun getOrCreateInstance(name: String): Collapse?
        }

    }
}
