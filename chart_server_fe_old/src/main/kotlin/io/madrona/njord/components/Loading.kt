package io.madrona.njord.components

import kotlinx.css.Display
import kotlinx.css.content
import kotlinx.css.display
import kotlinx.css.properties.IterationCount
import kotlinx.css.properties.TransitionDirection
import kotlinx.css.properties.s
import kotlinx.css.properties.steps
import kotlinx.css.quoted
import react.Props
import react.fc
import styled.animation
import styled.css
import styled.styledDiv

val Loading = fc<Props> {
    styledDiv {
        css {
            after {
                display = Display.inlineBlock
                content = "".quoted
                animation(duration = 1.s, iterationCount = IterationCount.infinite) {
                    steps(1, TransitionDirection.end)
                    0 {
                        content = "".quoted
                    }
                    25 {
                        content = ".".quoted
                    }
                    50 {
                        content = "..".quoted
                    }
                    75 {
                        content = "...".quoted
                    }
                    100 {
                        content = "".quoted
                    }
                }
            }
        }
        +"Loading"
    }
}