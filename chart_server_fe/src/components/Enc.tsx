//@ts-ignore
//eslint-disable-next-line import/no-webpack-loader-syntax
import maplibregl from '!maplibre-gl';

//@ts-ignore
//eslint-disable-next-line import/no-webpack-loader-syntax
import MapLibreWorker from '!maplibre-gl/dist/maplibre-gl-csp-worker';

import React, {useRef, useEffect, useState} from "react";
import Modal from 'react-bootstrap/Modal';
import Button from 'react-bootstrap/Button';
import Accordion from 'react-bootstrap/Accordion';
import '../App.css'
import 'maplibre-gl/dist/maplibre-gl.css';
import ChartQuery from './ChartQuery';
import {MapLibreEvent, MapMouseEvent, Map, MapGeoJSONFeature} from "maplibre-gl";
import {DepthUnit} from "../App";
import {Bounds} from "./ControlCharts";

maplibregl.workerClass = MapLibreWorker;

export class EncState {
    lng: number = parseFloat(window.localStorage.getItem("longitude") ?? "-122.4002");
    lat: number = parseFloat(window.localStorage.getItem("latitude") ?? "47.27984");
    zoom: number = parseFloat(window.localStorage.getItem("zoom") ?? "11.0");
}

export function storeEncState(state: EncState) {
    window.localStorage.setItem("longitude", `${state.lng}`)
    window.localStorage.setItem("latitude", `${state.lat}`)
    window.localStorage.setItem("zoom", `${state.zoom}`)
    console.log(`stored EncState = ${JSON.stringify(state)}`)
}

var destination: Bounds | null = null

export function setDestination(bounds: Bounds) {
    destination = bounds;
}

type EncProps = {
    depths: DepthUnit
}

export function Enc(props: EncProps) {
    const mapContainer = useRef(null);
    const map = useRef<Map | null>(null);
    const [show, setShow] = useState<MapGeoJSONFeature[] | null>(null);
    const handleClose = () => setShow(null);

    const encUpdater = (state: EncState) => {
        storeEncState(state);
    }

    useEffect(() => {
        let cMap: Map | null = map.current
        if (cMap) {
            let url = `/v1/style/${props.depths}`
            console.log(`loading style url ${url}`)
            cMap?.setStyle(url)
            return; //stops map from intializing more than once
        }

        let encState = new EncState();
        let newMap = new maplibregl.Map({
            container: mapContainer.current,
            style: `/v1/style/${props.depths}`,
            center: [encState.lng, encState.lat],
            zoom: encState.zoom
        });
        newMap.addControl(new maplibregl.NavigationControl(), 'top-right');
        new maplibregl.Marker({color: "#FF0000"})
            .setLngLat([-122.4002, 47.27984])
            .addTo(newMap);

        newMap.on('moveend', function (e: MapLibreEvent<MouseEvent | TouchEvent | WheelEvent | undefined>) {
            let center = e.target.getCenter();
            let zoom = e.target.getZoom();
            console.log(`moved to Zoom(${zoom}) ${center.toString()}`)
            encUpdater({
                lat: center.lat,
                lng: center.lng,
                zoom: zoom,
            });
        });
        newMap.on('click', function (e: MapMouseEvent) {
            const bbox = [
                [e.point.x - 5, e.point.y - 5],
                [e.point.x + 5, e.point.y + 5]
            ];
            let lnams = new Set<string>();
            let features = newMap.queryRenderedFeatures(bbox);
            let filtered = features.filter((each: MapGeoJSONFeature) => {
                let lnam = each.properties["LNAM"]
                if (lnam) {
                    let f = !lnams.has(lnam);
                    lnams.add(lnam)
                    return f;
                } else {
                    return true;
                }
            })
            setShow(filtered);
        });
        map.current = newMap

        if (destination) {
            newMap.fitBounds([[destination.leftLng, destination.topLat], [destination.rightLng, destination.bottomLat]])
            destination = null
        }
    });

    function clipboard() {
        navigator.clipboard.writeText(JSON.stringify(show));
    }

    return (
        <>
            <div ref={mapContainer} className="Fill"></div>
            <Modal show={show != null} onHide={handleClose} dialogClassName="modal-xl">
                <Modal.Header closeButton><Modal.Title>Chart Query</Modal.Title></Modal.Header>
                <Modal.Body>
                    <DisplayQuery object={show}/>
                    <Modal.Footer>
                        <Button variant="primary" onClick={clipboard}>Copy Json</Button>
                        <Button variant="secondary" onClick={handleClose}>Close</Button>
                    </Modal.Footer>
                </Modal.Body>
            </Modal>
        </>
    );
}

type DisplayQueryProps = {
    object: MapGeoJSONFeature[] | null
}

function DisplayQuery(props: DisplayQueryProps) {
    if (props.object) {
        return (
            <>
                <Accordion>
                    {
                        props.object.map((each, i) => {
                            return <ChartQuery key={i} feature={each} eventKey={`${i}`}/>
                        })
                    }
                </Accordion>
            </>
        );
    } else {
        return (<></>);
    }
}

