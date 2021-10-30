package io.madrona.njord.layers

class LayerFactory(
    private val layerables: Sequence<Layerable> = sequenceOf(
        Background(),
        Seaare(),
        Depare(),
        Depcnt(),
        NavLne(),
        Fairwy(),
        Slcons(),
        Ponton(),
        Pilpnt(),
        Hulkes(),
        Lndare(),
        Coalne(),
        Boyspp(),
        Achare(),
        Achbrt(),
        Achpnt(),
        Airare(),
        Bcncar(),
        Bcnisd(),
        Bcnlat(),
        Bcnsaw(),
        Bcnspp(),
        Berths(),
        Boycar(),
        Boyinb(),
        Boyisd(),
        Boylat(),
        Boysaw(),
        Bridge(),
        Buaare(),
        Buirel(),
        Buisgl(),
        Cgusta(),
        Chkpnt(),
        Cranes(),
        Ctnare(),
        Ctrpnt(),
        Ctsare(),
        Curent(),
        Damcon(),
        Daymar(),
        Dismar(),
        Dmpgrd(),
        Fogsig(),
        Forstc(),
        Fshfac(),
        Gatcon(),
        Morfac(),
        Lights(), //symbol lookup
        Soundg(),
        Ply(),
        Debug()
    )
) {

    fun layers(options: LayerableOptions) = layerables.map {
        it.layers(options)
    }.flatten().toList()
}