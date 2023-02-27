import {useRef, useEffect, useState, useContext} from 'react';
import Modal from 'react-bootstrap/Modal';
import Button from 'react-bootstrap/Button';
import Accordion from 'react-bootstrap/Accordion';

//@ts-ignore
//eslint-disable-next-line import/no-webpack-loader-syntax
import maplibregl from '!maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import "./Enc.css"
//@ts-ignore
//eslint-disable-next-line import/no-webpack-loader-syntax
import MapLibreWorker from '!maplibre-gl/dist/maplibre-gl-csp-worker';
import ChartQuery from './ChartQuery';
import {MapLibreEvent, MapMouseEvent} from "maplibre-gl";
import {EncContext} from "../App";

maplibregl.workerClass = MapLibreWorker;


export function Enc() {
    // let {dunit} = useParams();
    const mapContainer = useRef(null);
    const map = useRef(null);
    const [encState, setEncState] = useContext(EncContext);
    const [show, setShow] = useState(null);
    const handleClose = () => setShow(null);

    useEffect(() => {
        let cMap: maplibregl.Map = map.current
        if (cMap) {
            return; //stops map from intializing more than once
        }

        let newMap = new maplibregl.Map({
            container: mapContainer.current,
            style: `/v1/style/${encState.depthUnit}`,
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
            let newState = encState;
            newState.zoom = zoom
            newState.lat = center.lat
            newState.lng = center.lng
            setEncState(newState);
        });
        newMap.on('click', function (e: MapMouseEvent) {
            let features = newMap.queryRenderedFeatures(e.point);
            setShow(features);
        });
        map.current = newMap
    });

    function clipboard() {
        navigator.clipboard.writeText(JSON.stringify(show));
    }

    return (
        <>
            <div ref={mapContainer} className="enc"></div>
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

function DisplayQuery(props: any) {
    if (props.object) {
        return (
            <>
                <Accordion>
                    {Object.keys(props.object).map((each, i) => {
                        return (
                            <ChartQuery key={i} feature={props.object[each]} eventKey={`${each}`}/>
                        );
                    })}
                </Accordion>
            </>
        );
    } else {
        return (<></>);
    }
}

