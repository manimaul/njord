package io.madrona.njord.layers.set

import io.madrona.njord.layers.*

/**
 * http://localhost:9000/v1/about/s57objects
 *
 * bridge (BRIDGE)
 * coastline (COALNE)
 * depth area (DEPARE)
 * hulk (HULKES)
 * ice area (ICEARE)
 * land area (LNDARE)
 * mooring/warping facility (MORFAC)
 * pontoon (PONTON)
 * obstruction (OBSTRN)
 * cable overhead (CBLOHD)
 * canal (CANALS)
 * conveyor (CONVYR)
 *
 * <TODO:>
 *
 * dam (DAMCON)
 * deep water route centerline (DWRTCL)
 * dock area (DOCARE)
 * floating dock (FLODOC)
 * gate (GATCON)
 * lock basin (LOKBSN)
 * log pond (LOGPON)
 * offshore platform (OFSPLF)
 * oil barrier (OILBAR)
 * pylon/bridge support (PYLONS)
 * shoreline construction (SLCONS)
 * cartographic area ($AREAS)
 * notice mark (NOTMRK)
 * harbour basin (HRBBSN)
 * lock basin part (LKBSPT)
 * exceptional navigation structure (EXCNST) (note: IENC only)
 *
 **/
class BaseLayers {
    val layers = sequenceOf(
        Background(),
        Lndare(),
        Depare(),
        Iceare(),
        Coalne(),
        Hulkes(),
        Ponton(),
        Obstrn(), //todo: needs fix - see commencement bay compared with OpenCPN
        Morfac(),
        Bridge(),
        Cblohd(),
        Canals(),
        Convyr(),
        Slcons(),

        //todo: below layers need completion
        Damcon(),
        Dwrtcl(),
        Docare(),
        Flodoc(),
        Gatcon(),
        Lokbsn(),
        Logpon(),
        Ofsplf(),
        Oilbar(),
        Pylons(),
        Areas(),
        Notmrk(),
        Hrbbsn(),
        Lkbspt(),
        Excnst(),
    )
}
