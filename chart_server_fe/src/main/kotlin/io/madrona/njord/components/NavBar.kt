package io.madrona.njord.components

import mui.material.*
import react.Props
import react.dom.aria.ariaLabel
import react.fc

val NavBar = fc<Props> {
    AppBar {
        attrs.apply {
            position = "static"
        }
        Toolbar {
            attrs.apply {
                variant = "dense"
            }
            IconButton {
                attrs.apply {
                    edge = "start"
                    color = "inherit"
                    ariaLabel = "menu"
                }
                Menu {
                    attrs.apply {

                    }
                }
            }
            Typography {
                attrs.apply {
                    variant = "h6"
                }
                +"Njord"
            }
        }
    }
}