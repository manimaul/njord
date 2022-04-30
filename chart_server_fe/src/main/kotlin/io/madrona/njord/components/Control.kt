package io.madrona.njord.components

import csstype.ClassName
import io.madrona.njord.styles.AppRoutes
import kotlinx.css.paddingTop
import kotlinx.css.px
import react.Props
import react.dom.*
import react.fc
import react.router.dom.Link
import react.router.useLocation
import styled.css
import styled.styledDiv

enum class ControlTab {
    Charts,
    Symbols,
    SymbolSprites;

    companion object {
        fun getSelected(location: history.Location): ControlTab {
            return values().firstOrNull {
                "${AppRoutes.control}/${it.name}".equals(location.pathname, true)
            } ?: Charts
        }
    }
}

val Control = fc<Props> {
    val tab = ControlTab.getSelected(useLocation())
    styledDiv {
        css {
            paddingTop = 20.px
        }
        div(classes = "container") {
            h1 {
                +"Control Panel"
            }
            ul(classes = "nav nav-tabs") {
                ControlTab.values().forEach { eaTab ->
                    li(classes = "nav-item") {
                        Link {
                            +eaTab.name
                            attrs.also {
                                it.className = ClassName("nav-link${if (tab == eaTab) " active" else ""}")
                                it.to = "${AppRoutes.control}/${eaTab.name.lowercase()}"
                            }
                        }
                    }
                }
            }
            styledDiv {
                css {
                    paddingTop = 10.px
                }
                when (tab) {
                    ControlTab.Charts -> ControlCharts {}
                    ControlTab.Symbols -> ControlSymbols {}
                    ControlTab.SymbolSprites -> ControlSymbolSprites {}
                }
            }
        }
    }
}
