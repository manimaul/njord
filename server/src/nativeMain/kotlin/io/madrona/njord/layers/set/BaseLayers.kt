package io.madrona.njord.layers.set

import io.madrona.njord.layers.*
import io.madrona.njord.layers.base.AntarcticIceShelves
import io.madrona.njord.layers.base.Bathymetry
import io.madrona.njord.layers.base.Coastline
import io.madrona.njord.layers.base.GlaciatedAreas
import io.madrona.njord.layers.base.Lakes
import io.madrona.njord.layers.base.Land
import io.madrona.njord.layers.base.MinorIslands
import io.madrona.njord.layers.base.Ocean
import io.madrona.njord.layers.base.Playas
import io.madrona.njord.layers.base.Reefs
import io.madrona.njord.layers.base.RiversLakeCenterlines


class BaseLayers {

    val layers = sequenceOf(
        Background(),
        Ocean(),
        Bathymetry(),
        GlaciatedAreas(),
        Reefs(),
        Land(),
        Playas(),
        RiversLakeCenterlines(),
        Lakes(),
        AntarcticIceShelves(),
        MinorIslands(),
        Coastline(),
    )
}
