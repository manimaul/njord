package io.madrona.njord

import com.willkamp.vial.api.ResponseBuilder
import com.willkamp.vial.api.VialServer

fun main() {
    VialServer.create().httpGet("/") { _, responseBuilder: ResponseBuilder ->
        responseBuilder.setBodyHtml("""
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8" />
            <title>Display a map</title>
            <meta name="viewport" content="initial-scale=1,maximum-scale=1,user-scalable=no" />
            <script src="https://api.mapbox.com/mapbox-gl-js/v1.10.1/mapbox-gl.js"></script>
            <link href="https://api.mapbox.com/mapbox-gl-js/v1.10.1/mapbox-gl.css" rel="stylesheet" />
            <style>
            	body { margin: 0; padding: 0; }
            	#map { position: absolute; top: 0; bottom: 0; width: 100%; }
            </style>
            </head>
            <body>
            <div id="map"></div>
            <script>
            	mapboxgl.accessToken = '${System.getProperty("MAPBOX_TOKEN")}';
            var map = new mapboxgl.Map({
            container: 'map', // container id
            style: 'mapbox://styles/mapbox/streets-v11', // stylesheet location
            center: [-122, 48], // starting position [lng, lat]
            zoom: 9 // starting zoom
            });
            </script>
            </body>
            </html>
        """.trimIndent())
    }.listenAndServeBlocking()
}