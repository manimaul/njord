import React, {useRef, useEffect, useState} from 'react';
// eslint-disable-next-line import/no-webpack-loader-syntax
import maplibregl from '!maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import "./Enc.css"
// eslint-disable-next-line import/no-webpack-loader-syntax
import MapLibreWorker from '!maplibre-gl/dist/maplibre-gl-csp-worker';

maplibregl.workerClass = MapLibreWorker;
export function Enc() {
    const mapContainer = useRef(null);
    const map = useRef(null);
    const [lng] = useState(-122.44);
    const [lat] = useState(47.257);
    const [zoom] = useState(11.0);

    useEffect(() => {
        if (map.current) return; //stops map from intializing more than once
        map.current = new maplibregl.Map({
            container: mapContainer.current,
            style: '/v1/style/meters',
            center: [lng, lat],
            zoom: zoom
        });
        map.current.addControl(new maplibregl.NavigationControl(), 'top-right');
        new maplibregl.Marker({color: "#FF0000"})
            .setLngLat([-122.4002, 47.27984])
            .addTo(map.current);
    });

    return (
        <div ref={mapContainer} className="enc"></div>
    );
}
