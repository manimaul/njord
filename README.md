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

----------

# Dev - Quick Start

You can ignore the system requirements if you have Docker & Docker Compose on your development. Build & bring up the 
whole stack with `docker-compose` from the project root directory:

```shell
docker-compose up
```

After waiting a while for all the image layers to be pulled and the project to build you should be able to visit:
* http://localhost:9000/v1/content/upload.html (Upload a zip file of S57 files)
* http://localhost:9000/v1/content/index.html (View the charts)

Note: This is not properly setup (yet) for iterative development inside of containers.