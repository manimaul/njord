package io.madrona.njord.components

import kotlinx.css.*
import react.Props
import react.dom.h4
import react.fc
import styled.css
import styled.styledDiv


val Chart = fc<Props> {
    styledDiv {
        css {
            height = 100.vh
        }
        styledDiv {
            css {
                height = 100.vh
                display = Display.flex
                flexDirection = FlexDirection.column
            }
            styledDiv {
                css {
                    flex(0.0, 1.0, FlexBasis.auto)
                }
                h4 {
                    +"Njord - S57 Server"
                }
            }
            styledDiv {
                css {
                    flex(1.0, 1.0, FlexBasis.auto)
                }
                mapLibre()
            }
            styledDiv {
                css {
                    flex(0.0, 1.0, FlexBasis.auto)
                }
                h4 {
                    +"Footer"
                }
            }
        }
    }
}