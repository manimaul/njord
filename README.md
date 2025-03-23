# Njord 

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Status: This project is still very much a WORK IN PROGRESS.
Live Demo: https://openenc.com

<p>
    <img alt="Njord" src="./chart_server_fe/src/njord.jpg" width=500 >
    <img alt="Screenshot" src="./screenshot.png" width=500 >
</p>

### Marine Electronic Navigational Chart (ENC) Server

Njord consumes S-57 IHO transfer standard for digital hydrographic data. S-57 format chart data is published by 
hydrographic offices such as NOAA. https://www.charts.noaa.gov/ENCs/ENCs.shtml 

Njord displays ENC charts but does **NOT** strictly follow the IHO S-52 specifications for chart content and display.

S-52 display and S-57 data standards can be found here: https://iho.int/en/standards-and-specifications

----------

# Development System Requirements

* OpenJDK 17
* Gdal 3.6.2 **with Java bindings**
  * Note: Homebrew Gdal does not come with Java bindings and the osgeo tap is broken. 
          See [docs/gdal/README.md](docs/gdal/README.md) for building gdal from source with java via `brew`.
* PostGIS 13
  * See [chart_server_db/README.md](chart_server_db/README.md) for running PostGIS in a container for development. 

----------

# Local Development Quick Start

Bring up database
```shell
cd chart_server_db
docker-compose up
```

Build front end debug
```shell
./gradlew :web:jsBrowserDistribution
```

Bring up api
```shell
./gradlew :server:runDebugExecutable
```

Bring up front end with hot-reload
```shell
./gradlew :web:jsRun --continuous
```

----------

## Prod Dry run with install dist

```shell
export JAVA_OPTS="-Dcharts.webStaticContent=$HOME/source/njord/web/build/dist/js/productionExecutable -Djava.library.path=/opt/gdal/jni"
export CHART_SERVER_OPTS="-Dcharts.adminUser=test"
./gradlew :chart_server:installDist
./gradlew :web:jsBrowserDistribution
./chart_server/build/install/chart_server/bin/chart_server
```

----------

# Docs

[System Design Notes](docs/DESIGN.md)


# Install on Raspberry Pi

```shell
sudo apt update && sudo apt upgrade
sudo apt install ./njord<version>.deb
sudo apt install ./gdal_3.10.0-1_arm64.deb
sudo systemctl enable postgresql.service
sudo bash -c "echo \"listen_addresses = 'localhost'\" >> /etc/postgresql/15/main/postgresql.conf"
sudo systemctl restart postgresql.service
sudo /opt/chart_server/njord_setup.sh
```

# Prometheus Grafana (optional)
```shell
sudo apt install -y apt-transport-https software-properties-common wget
wget https://github.com/prometheus/jmx_exporter/releases/download/1.1.0/jmx_prometheus_javaagent-1.1.0.jar /opt/chart_server/jmx-agent.jar
sudo mkdir -p /etc/apt/keyrings/
wget -q -O - https://apt.grafana.com/gpg.key | gpg --dearmor | sudo tee /etc/apt/keyrings/grafana.gpg > /dev/null
echo "deb [signed-by=/etc/apt/keyrings/grafana.gpg] https://apt.grafana.com stable main" | sudo tee -a /etc/apt/sources.list.d/grafana.list
sudo apt update && sudo apt install prometheus grafana
```

Add the following to /etc/prometheus/prometheus.yml 
```yaml
scrape_configs:
  - job_name: njord 
    scrape_interval: 5s
    scrape_timeout: 5s
    static_configs:
      - targets: ['localhost:5000']
```

```shell
sudo systemctl restart prometheus 
sudo systemctl status prometheus 

sudo systemctl enable grafana-server
sudo systemctl start grafana-server
sudo systemctl status grafana-server
```

Prometheus runs on port 9090
Grafana runs on port 3000