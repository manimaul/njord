package io.madrona.njord.model

import com.fasterxml.jackson.annotation.JsonProperty

typealias S57ObjAcronyms =  Map<String, MutableList<S57Symbol>>

data class S57Symbol(
    @JsonProperty("SY") val symbol: String?,
    @JsonProperty("ATT") val attributes: List<Map<String, List<Int>>>
)


/**
Object Keys (Layer):

######
ACHARE
ACHBRT
ACHPNT
AIRARE
BCNCAR
BCNISD
BCNLAT
BCNSAW
BCNSPP
BERTHS
BOYCAR
BOYINB
BOYISD
BOYLAT
BOYSAW
BRIDGE
BUAARE
BUIREL
BUISGL
CGUSTA
CHKPNT
CRANES
CTNARE
CTRPNT
CTSARE
CURENT
DAMCON
DAYMAR
DISMAR
DMPGRD
FOGSIG
FORSTC
FSHFAC
GATCON

 */

/**
Attribute keys:

BOYSHP
BCNSHP
COLPAT
CATLAM
CATCAM
CATSPM
CONVIS
COLOUR
FUNCTN
OBJNAM
CATCHP
ORIENT
CURVEL
CATDAM
CATDIS
TOPSHP
HUNITS
WTWDIS
CATFIF
CATGAT
 */

