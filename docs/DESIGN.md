# System Overview

Njord serves MVT (Mapbox Vector Tiles) and S57 themed Mapbox styles (sprites, style json, etc). When you visit https://openenc.com/control/charts_installer and upload a zip file of S57 files here is what happens:
* The zip file is form uploaded to Njord which returns a Json response
* Njord unzips the file in a temp directory and returns a Json response with a uuid and meta data describing the temp files
* The WebUI looks at the response and opens a WebSocket to Njord using the uuid
* Njord starts processing the S57 files and sends WebSocket messages back about the progress
  (The UI is super RAW but functional)

## S57 Ingestion
Ok, here's how Njord processes the S57 files:
* Each chart in the unzip directory is read and stored as a "chart" record in the PostGIS data base using DSID and M_COVR layers (M_COVR gives us the chart coverage polygon)
* We store chart text files as binary json in the chart_txt row and dsid_props (key values) also as binary json
* We also store the chart scale in a row as well as calculating the mapbox z value for that scale (we could probably omit these rows as the data is already there in the binary json)
* The chart table indexes on the M_COVR coverage polygon for fast queries
* The remaining features in each of the layers in each S57 file are read and converted to a GeoJson FeatureCollection and stored in the "features" table. Note that we convert all geometries to 4326
  (schema: https://github.com/manimaul/njord/blob/master/chart_server_db/postgres_init/scripts/up.sql )

You can see the corresponding endpoints here: https://github.com/manimaul/njord/blob/master/chart_server/src/jvmMain/kotlin/io/madrona/njord/ChartServerApp.kt such as StyleHandler

## MVT Tiles
When a tile is fetched we do the following:
* Lookup charts within the tile envelope sorted by scale
* Lookup features within the tile geometry for each chart id
* Clip each geometry based on what was already encoded (covered).
* Add clipped geometry and key value properties to the encoded MVT tile.
  https://github.com/manimaul/njord/blob/master/chart_server/src/jvmMain/kotlin/io/madrona/njord/endpoints/TileHandler.kt
  https://github.com/manimaul/njord/blob/master/chart_server/src/jvmMain/kotlin/io/madrona/njord/geo/TileEncoder.kt

## Styling

Here are all the style json layers: https://github.com/manimaul/njord/tree/master/chart_server/src/jvmMain/kotlin/io/madrona/njord/layers
Not all of these are complete and there are more S57 objects to add.

You may have noticed that the TileEncoder looks up layer symbols and adds them to the encoded tile feature(s) properties:
https://github.com/manimaul/njord/blob/master/chart_server/src/jvmMain/kotlin/io/madrona/njord/geo/TileEncoder.kt

You can see the corresponding symbol lookups in the style json: https://openenc.com/v1/style/meters/day
eg icon-image: `["get","SY"]`
So, SY is added to the S57 feature properties.

You can view features by chart and layer as geojson: https://openenc.com/v1/geojson?chart_id=1&layer_name=BOYSPP
This endpoint isn't really necessary for the chart display but can help debug. Note that SY is not added to the geojson.

Other layers like LNDARE https://github.com/manimaul/njord/blob/master/chart_server/src/jvmMain/kotlin/io/madrona/njord/layers/Lndare.kt just use the corresponding S57 object and has fill and line style rules.

The DEPARE layer https://github.com/manimaul/njord/blob/master/chart_server/src/jvmMain/kotlin/io/madrona/njord/layers/Depare.kt can choose colors based on the depth.

Another interesting Mabox layer style is SOUNDG https://github.com/manimaul/njord/blob/master/chart_server/src/jvmMain/kotlin/io/madrona/njord/layers/Soundg.kt. 
