package io.madrona.njord.components

import react.Props
import react.dom.div
import react.dom.h1
import react.fc

val About = fc<Props> {
    div {
        h1 {
            + "Njord S57 Chart Server"
        }
        + "Version ${js("NJORD_VERSION")}"
    }
}