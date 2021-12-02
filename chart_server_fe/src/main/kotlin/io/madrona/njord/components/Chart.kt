package io.madrona.njord.components

import kotlinx.css.*
import react.Props
import react.fc
import styled.css
import styled.styledDiv


val Chart = fc<Props> {
    styledDiv {
        css {
            height = 100.vh
            width = 100.vw
        }
        mapLibre()
    }
}
