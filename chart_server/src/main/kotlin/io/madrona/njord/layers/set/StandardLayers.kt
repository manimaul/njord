package io.madrona.njord.layers.set

import io.madrona.njord.layers.*


/**
 * https://openenc.com/v1/about/s57objects
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
        Depare(),
        Lndare(),
        Lakshr(),
        Buisgl(),
        Buaare(),
        ObstrnArea(),
        Slcons(),
        Lndrgn(), // has area patterns - should be above other land layers
        Drgare(),
        Iceare(),
        Coalne(),
        Causwy(),
        Canals(),
        Rivers(),
        Lokbsn(),
        Airare(),
        Runway(),
        Dykcon(),
        Siltnk(),
        Ctrpnt(),

        // water boundary layers
        Tsslpt(),
        Tssbnd(),
        Tselne(),
        Twrtpt(),
        Tsezne(),
        Dwrtpt(),
        Dwrtcl(),
        Mipare(),
        Logpon(),
        Icnare(),
        Achbrt(),
        Achare(),
        Cblare(),
        Cblsub(),
        Cblohd(),
        Resare(),
        Ctnare(),
        Ctsare(),
        Wedklp(),
        Fairwy(),
        Fnclne(),
        Feryrt(),
        Fshgrd(),
        Fshfac(),
        Marcul(),
        Forstc(),
        Dmpgrd(),
        Pipare(),
        Istzne(),
        Rectrc(),
        Rctlpt(),
        Navlne(),
        Dismar(),
        Prcare(),
        Sndwav(),
        Unsare(),


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
        Cranes(),
        Pylons(),
        Daymar(),
        Pilbop(),
        Pilpnt(),
        Obstrn(),
        Ospare(),
        Sistat(),
        Sistaw(),

        //low pri labels
        LndareLabel(),
        Seaare(),

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
        Lndmrk(),
        Topmar(),

        // lights
        Litflt(),
        Litves(),
        Lights(),
        Prdare(),

        //high pri labels
        Soundg(),
        Curent(),

        //todo: below layers need completion
        Chkpnt(),
        Cgusta(),
        Buirel(),
        Berths(),
        Achpnt(),

        // todo:
        Radlne(),
        Radrng(),
        Radrfl(),
        Rtpbcn(),
        Rdocal(),
        Rcrtcl(),
        Retrfl(),
        Splare(),
        Slogrd(),
        Subtln(),
        Swpare(),
        Tsscrs(),
        Tssron(),
        Tunnel(),
        Newobj(),
//    $texts(),
//    M_nsys(),
//    $csymb(),
//    $texts(),
    )
}
