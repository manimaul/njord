# Chart Server

#### Docs

[MAP_TILES_DESIGN_DOC](../docs/MAP_TILES_DESIGN_DOC.md)

#### Features: 

* WIP

#### Todo:

* Natural Earth Data - Render via Mapbox MVTs
* Read S57 files - Render via Mapbox MVTs
* Read BSB files - Render as PNG tiles
* 100% offline capable (all js & assets serve locally)
* Debian Packaging
* Configurable DB (Postgres & Sqlite)
* GPS Position - via NMEA over Tcp
* AIS - via NMEA over Tcp
* Caching Proxy - Online Style(s) (satellite)
* Mobile client - charts sync

#### Stack:

* [Netty](https://netty.io/)
* [Gdal](https://gdal.org/)
* [JTS](https://github.com/locationtech/jts)
* [Mapbox GL JS](https://docs.mapbox.com/mapbox-gl-js/api/)
    * [MVT - Mapbox Vector Tile](https://docs.mapbox.com/vector-tiles/reference/)
* [Kotlin-JVM](https://kotlinlang.org/docs/reference/server-overview.html)
    * [Vial](https://github.com/manimaul/vial/)
* [Kotlin-JS](https://kotlinlang.org/docs/reference/js-overview.html)

-----------------------------------------------

#### Dev Setup MacOS

```bash
brew tap osgeo/osgeo4mac
brew install osgeo/osgeo4mac/osgeo-gdal
```

Note: Gdal version in build.gradle.kts should match the native 
version installed by brew `org.gdal:gdal:3.1.0` 

#### Dev Setup Linux

```bash
sudo apt install libgdal-java
```

https://github.com/maputnik/editor/wiki
docker run -it --rm -p 8888:8888 maputnik/editor


tippecanoe -zg -o chart.mbtiles --coalesce-densest-as-needed --extend-zooms-if-still-dropping \
ACHARE.json BOYSPP.json C_ASSO.json DEPARE.json FERYRT.json LNDMRK.json M_NPUB.json M_SDAT.json PILPNT.json RIVERS.json SLCONS.json WATTUR.json \
ADMARE.json BUAARE.json CBLARE.json DEPCNT.json LIGHTS.json LNDRGN.json M_NSYS.json M_VDAT.json PRCARE.json SBDARE.json SOUNDG.json WEDKLP.json \
BCNSPP.json BUISGL.json COALNE.json DMPGRD.json LNDARE.json MAGVAR.json MORFAC.json OBSTRN.json RDOCAL.json SEAARE.json TWRTPT.json WRECKS.json \
BOYLAT.json C_AGGR.json CTNARE.json DSID.json LNDELV.json M_COVR.json M_QUAL.json OFSPLF.json RESARE.json SILTNK.json UWTROC.json

docker run --rm -it -v $(pwd):/data -p 8080:80 maptiler/tileserver-gl
