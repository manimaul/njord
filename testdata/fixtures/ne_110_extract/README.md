# ne_110_extract

Natural Earth 1:110m coastline (`ne_110m_coastline`, Natural Earth version 4.1.0, public domain),
copied unchanged from `libgdal/src/nativeTest/resources/ne_110_extract`. One shapefile with its
`.dbf`, `.shx`, `.prj`, `.cpg` sidecars and Natural Earth's README.

**Pins:** the shapefile read contract for the base map (P-IN-6, `s57/natural_earth.json`): layer
name, feature count and GDAL field mapping as `NaturalEarthIngest` inserts them into
`base_features`. `NaturalEarthIngest.normalizeLayerName` / `scaleFromFilename` are pinned in the
server module from this file's name (`ne_110m_` -> scale 110 000 000, layer `coastline`).
