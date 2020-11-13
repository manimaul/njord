package io.madrona.njord

fun Any.resourceString(path: String) : String? {
    return this::class.java.classLoader?.getResource(path)?.readText()
}

/*
tippecanoe -zg -o chart.mbtiles --coalesce-densest-as-needed --extend-zooms-if-still-dropping \
ACHARE.json BOYSPP.json C_ASSO.json DEPARE.json FERYRT.json LNDMRK.json M_NPUB.json M_SDAT.json PILPNT.json RIVERS.json SLCONS.json WATTUR.json \
ADMARE.json BUAARE.json CBLARE.json DEPCNT.json LIGHTS.json LNDRGN.json M_NSYS.json M_VDAT.json PRCARE.json SBDARE.json SOUNDG.json WEDKLP.json \
BCNSPP.json BUISGL.json COALNE.json DMPGRD.json LNDARE.json MAGVAR.json MORFAC.json OBSTRN.json RDOCAL.json SEAARE.json TWRTPT.json WRECKS.json \
BOYLAT.json C_AGGR.json CTNARE.json DSID.json LNDELV.json M_COVR.json M_QUAL.json OFSPLF.json RESARE.json SILTNK.json UWTROC.json
 */