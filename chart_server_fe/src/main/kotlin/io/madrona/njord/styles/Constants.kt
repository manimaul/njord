package io.madrona.njord.styles

import io.madrona.njord.components.ControlTab

object AppRoutes {
    const val home = "/v1/app"
    const val about = "/v1/app/about"

    const val control = "/v1/app/control"
    const val controlPage = "$control/:${Params.page}"
    const val controlSymbolsPage = "$controlPage/:${Params.symbol}"
    const val controlSymbolsAttPage = "$controlSymbolsPage/:${Params.att}"

    object Params {
        const val symbol = "symbol"
        const val att = "att"
        const val page = "page"
    }
}
