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
 * airport / airfield (AIRARE)
 * anchor berth (ACHBRT)
 * anchorage area (ACHARE)
 * beacon cardinal (BCNCAR)
 * beacon isolated danger (BCNISD)
 * beacon lateral (BCNLAT)
 * beacon safe water (BCNSAW)
 * beacon special purpose/general (BCNSPP)
 * building single (BUISGL)
 * built-up area (BUAARE)
 * buoy cardinal (BOYCAR)
 * buoy installation (BOYINB)
 * buoy isolated danger (BOYISD)
 * buoy lateral (BOYLAT)
 * buoy safe water (BOYSAW)
 * buoy special purpose/general (BOYSPP)
 * cable area (CBLARE)
 * cable submarine (CBLSUB)
 * cargo transshipment area (CTSARE)
 * causeway (CAUSWY)
 * caution area (CTNARE)
 * crane (CRANES)
 * daymark (DAYMAR)
 * deep water route part (DWRTPT)
 * dredged area (DRGARE)
 * dumping ground (DMPGRD)
 * dyke (DYKCON)
 * fairway (FAIRWY)
 * fence/wall (FNCLNE)
 * ferry route (FERYRT)
 * ferry route (feryrt)
 * fishing ground (FSHGRD)
 * fog signal (FOGSIG)
 * fortified structure (FORSTC)
 * gate (GATCON)
 * hulk (HULKES)
 * incineration area (ICNARE)
 * inshore traffic zone (ISTZNE)
 * lake shore (LAKSHR)
 * land region (LNDRGN)
 * landmark (LNDMRK)
 * light (LIGHTS)
 * light float (LITFLT)
 * light vessel (LITVES)
 * marine farm/culture (MARCUL)
 * military practice area (MIPARE)
 * navigation line (NAVLNE)
 * offshore production area (OSPARE)
 * pile (PILPNT)
 * pilot boarding place (PILBOP)
 * pipeline area (PIPARE)
 * precautionary area (PRCARE)
 * production / storage area (PRDARE)
 * radar line (RADLNE)
 * radar range (RADRNG)
 * radar reflector (RADRFL)
 * radar transponder beacon (RTPBCN)
 * radio calling-in point (RDOCAL)
 * recommended route centerline (RCRTCL)
 * recommended track (RECTRC)
 * recommended traffic lane part (RCTLPT)
 * restricted area (RESARE)
 * retro-reflector (RETRFL)
 * river (RIVERS)
 * runway (RUNWAY)
 * sand waves (SNDWAV)
 * sea area / named water area (SEAARE)
 * sea-plane landing area (SPLARE)
 * signal station traffic (SISTAT)
 * signal station warning (SISTAW)
 * silo / tank (SILTNK)
 * sloping ground (SLOGRD)
 * submarine transit lane (SUBTLN)
 * swept area (SWPARE)
 * text ($TEXTS)
 * top mark (TOPMAR)
 * traffic separation line (TSELNE)
 * traffic separation scheme boundary (TSSBND)
 * traffic separation scheme crossing (TSSCRS)
 * traffic separation scheme lane part (TSSLPT)
 * traffic separation scheme roundabout (TSSRON)
 * traffic separation separation zone (TSEZNE)
 * tunnel (TUNNEL)
 * two-way route part (TWRTPT)
 * unsurveyed area (UNSARE)
 * new object (NEWOBJ)
 * navigational system of marks (M_NSYS)
 * cartographic symbol ($CSYMB)
 * text ($TEXTS)
 */
class StandardLayers {
    val layers = sequenceOf(
        Background(),

        //land / base layers
        Lndare(),
        Buisgl(),
        Buaare(),
        Slcons(),
        Lndrgn(), // has area patterns - should be above other land layers
        Depare(),
        Drgare(),
        Iceare(),
        Coalne(),
        Causwy(),
        Canals(),
        Lokbsn(),

        // water boundary layers
        Tssbnd(),
        Tselne(),
        Dwrtpt(),
        Dwrtcl(),
        Mipare(),
        Logpon(),
        Achbrt(),
        Achare(),
        Cblare(),
        Cblsub(),
        Cblohd(),

        // obstructions
        Hulkes(),
        Ponton(),
        Morfac(),
        Bridge(),
        Convyr(),
        Damcon(),
        Docare(),
        Flodoc(),
        Gatcon(),
        Ofsplf(),
        Oilbar(),
        Airare(),
        Ctsare(),
        Ctnare(),
        Cranes(),
        Pylons(),
        Daymar(),
        Pilbop(),
        Obstrn(),

        // buoys and markers
        Fogsig(),
        Bcncar(),
        Bcnisd(),
        Bcnlat(),
        Bcnsaw(),
        Bcnspp(),
        Boycar(),
        Boyinb(),
        Boyisd(),
        Boylat(),
        Boysaw(),
        Boyspp(),

        // lights
        Lights(),

        //todo: below layers need completion
        Dmpgrd(),
        Dykcon(),
        Fairwy(),
        Fnclne(),
        Feryrt(),
//        feryrt(),
        Fshgrd(),
        Forstc(),
        Icnare(),
        Istzne(),
        Lakshr(),
        Lndmrk(),
        Litflt(),
        Litves(),
        Marcul(),
        Navlne(),
        Ospare(),
        Pilpnt(),
        Pipare(),
        Prcare(),
        Prdare(),
        Radlne(),
        Radrng(),
        Radrfl(),
        Rtpbcn(),
        Rdocal(),
        Rcrtcl(),
        Rectrc(),
        Rctlpt(),
        Resare(),
        Retrfl(),
        Rivers(),
        Runway(),
        Sndwav(),
        Seaare(),
        Splare(),
        Sistat(),
        Sistaw(),
        Siltnk(),
        Slogrd(),
        Subtln(),
        Swpare(),
//        $texts(),
        Topmar(),
        Tsscrs(),
        Tsslpt(),
        Tssron(),
        Tsezne(),
        Tunnel(),
        Twrtpt(),
        Unsare(),
        Newobj(),
//        M_nsys(),
//    $csymb(),
//    $texts(),
    )
}
