#!/usr/bin/env python3

import os

template = """package io.madrona.njord.layers

import io.madrona.njord.model.*

class CLASS : Layerable(autoSymbol = true) {
    override val key = "KEY"

    override fun layers(options: LayerableOptions) = sequenceOf(
            Layer(
                    id = "${key}_point",
                    type = LayerType.SYMBOL,
                    sourceLayer = key,
                    filter = listOf(Filters.any, Filters.eqTypePoint),
                    layout = Layout(
                            symbolPlacement = Placement.POINT,
                            iconImage = listOf("get", "SY"),
                            iconAnchor = Anchor.BOTTOM,
                            iconAllowOverlap = true,
                            iconKeepUpright = true,
                    )
            )
    )
}
"""

layers = [
    "Morfac",
    # "Achare",
    # "Achbrt",
    # "Achpnt",
    # "Airare",
    # "Bcncar",
    # "Bcnisd",
    # "Bcnlat",
    # "Bcnsaw",
    # "Bcnspp",
    # "Berths",
    # "Boycar",
    # "Boyinb",
    # "Boyisd",
    # "Boylat",
    # "Boysaw",
    # "Bridge",
    # "Buaare",
    # "Buirel",
    # "Buisgl",
    # "Cgusta",
    # "Chkpnt",
    # "Cranes",
    # "Ctnare",
    # "Ctrpnt",
    # "Ctsare",
    # "Curent",
    # "Damcon",
    # "Daymar",
    # "Dismar",
    # "Dmpgrd",
    # "Fogsig",
    # "Forstc",
    # "Fshfac",
    # "Gatcon"
]

for ea in layers:
    print(ea)
    f = "/Users/williamkamp/source/madrona/njord/chart_server/src/main/kotlin/io/madrona/njord/layers/{}.kt".format(ea)
    if not os.path.exists(f):
        print(f)
    else:
        print("skipping {}".format(ea))
    with open(f, 'w', encoding="utf-8") as kt:
        kt.write(template.replace("CLASS", ea).replace("KEY", ea.upper()))
    # print(template.replace("CLASS", ea).replace("KEY", ea.upper()))
