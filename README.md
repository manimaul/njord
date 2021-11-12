# Njord - S57 Chart Server

Demo / Staging Deployment: https://s57dev.mxmariner.com/v1/content/index.html#15.01/47.28597/-122.4102

### Status: This project is still very much a WORK IN PROGRESS.

----------

# System Requirements

* OpenJDK 11
* Gdal 3.2+ with Java bindings
  * Note: Homebrew Gdal does not come with Java bindings and the osgeo tap is broken. 
          See [docs/gdal/README.md](docs/gdal/README.md) for building gdal from source with java via `brew`.
* PostGIS 13
  * See [chart_server_db/README.md](chart_server_db/README.md) for running PostGIS in a container for development. 
