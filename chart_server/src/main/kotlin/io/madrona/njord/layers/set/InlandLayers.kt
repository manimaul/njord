package io.madrona.njord.layers.set

import io.madrona.njord.layers.*


/**
 * http://localhost:9
 *
 * harbour basin (HRBBSN)
 * notice mark (NOTMRK)
 * lock basin part (LKBSPT)
 * lock basin (LOKBSN)
 * exceptional navigation structure (EXCNST) (note: IENC only)
 */
class InlandLayers {
    val layers = sequenceOf(
        Lokbsn("lokbsn"),
        //todo: inland ENC https://www.agc.army.mil/Portals/75/docs/echart/Inland_ENC_Encoding_Guide_2_2_0.pdf
        // below layers need completion
        Hrbbsn(),
        Notmrk(),
        Lkbspt(),
        Excnst(),
    )
}
