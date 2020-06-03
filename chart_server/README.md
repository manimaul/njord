# Chart Server

#### Features: 

* WIP

#### Todo:

* Natural Earth Data - Render via Mapbox MVTs
* Read S57 - Render via Mapbox MVTs
* 100% offline capable (all js & assets serve locally)
* Debian Packaging
* Configurable DB (Postgres & Sqlite)
* GPS Position - via NMEA over Tcp
* AIS - via NMEA over Tcp

#### Stack:

* [Netty](https://netty.io/)
* [Gdal](https://gdal.org/)
* [JTS](https://github.com/locationtech/jts)
* [Mapbox GL JS](https://docs.mapbox.com/mapbox-gl-js/api/)
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

Todo: