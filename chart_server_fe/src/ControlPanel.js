import React, {useState} from "react";
import {Tab, Table, Tabs} from "react-bootstrap";
import {useRequest} from "./Effects";

function pathToFullUrl(path) {
    return `${window.location.protocol}//${window.location.host}${path}`
}

const endPoints = [
        "/v1/content/sprites/simplified.png",
        "/v1/about/version",
        "/v1/about/s57objects",
        "/v1/about/s57attributes",
        "/v1/tile_json",
        "/v1/style/meters",
        "/v1/chart?id=1",
        "/v1/chart_catalog",
        "/v1/geojson?chart_id=17&layer_name=BOYSPP",
        "/v1/tile/0/0/0",
        "/v1/icon/.png",
        "/v1/content/fonts/Roboto Bold/0-255.pbf",
        "/v1/content/sprites/simplified.json",
        "/v1/content/sprites/simplified.png",
]
function Endpoints() {
    return (
        <div>
            <h2>GET Endpoints</h2>
            <ol>
                {endPoints.map(each => {
                    return (<li><a href={pathToFullUrl(each)}>{each}</a></li>)
                })}
            </ol>
            <a href="/api" target="_blank">API</a>
        </div>
    )
}
function Charts() {
    const [charts, setCharts] = useState([])
    useRequest("/v1/chart_catalog", setCharts)

    return (
        <div>
            <Table striped bordered hover variant="dark" className="w-50">
                <thead>
                <tr>
                    <th>ID</th>
                    <th>Name</th>
                </tr>
                </thead>
                <tbody>
                {charts.map(each => {
                    return (
                        <tr>
                            <td>{each.id}</td>
                            <td>{each.name}</td>
                        </tr>
                    )
                })}
                </tbody>
            </Table>
        </div>
    )
}
function ControlPanel() {
    const [key, setKey] = useState('charts');

    return (
        <div className="container-fluid Content">
            <h1>ControlPanel</h1>
            <Tabs
                id="controlled-tab-example"
                activeKey={key}
                onSelect={(k) => setKey(k)}
                className="mb-3"
                variant="pills"
            >
                <Tab eventKey="charts" title="Charts">
                    <Charts />
                </Tab>
                <Tab eventKey="symbols" title="Symbols">
                    <p>foo</p>
                </Tab>
                <Tab eventKey="sprites" title="Sprites">
                    <p>foo</p>
                </Tab>
                <Tab eventKey="endpoints" title="Endpoints">
                    <Endpoints />
                </Tab>
            </Tabs>
        </div>
    );
}

export default ControlPanel;
