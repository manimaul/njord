package io.madrona.njord.layers.set

import io.madrona.njord.layers.Bridge

/**
 * http://localhost:9000/v1/about/s57objects
 *
 * bridge (BRIDGE)
 * cable overhead (CBLOHD)
 * canal (CANALS)
 * coastline (COALNE)
 * conveyor (CONVYR)
 * dam (DAMCON)
 * deep water route centerline (DWRTCL)
 * depth area (DEPARE)
 * dock area (DOCARE)
 * floating dock (FLODOC)
 * gate (GATCON)
 * hulk (HULKES)
 * ice area (ICEARE)
 * land area (LNDARE)
 * lock basin (LOKBSN)
 * log pond (LOGPON)
 * mooring/warping facility (MORFAC)
 * obstruction (OBSTRN)
 * offshore platform (OFSPLF)
 * oil barrier (OILBAR)
 * pontoon (PONTON)
 * pylon/bridge support (PYLONS)
 * shoreline construction (SLCONS)
 * cartographic area ($AREAS)
 * notice mark (NOTMRK)
 * harbour basin (HRBBSN)
 * lock basin part (LKBSPT)
 * exceptional navigation structure (EXCNST) (note: IENC only)
 **/
class BaseLayers {
    val layers = sequenceOf(
        Bridge()
    )
}