import React, {useEffect, useState} from "react";
import {Tab, Table, Tabs} from "react-bootstrap";
import {useRequest} from "./Effects";
import ChartSymbols from "./ControlSymbols";
import {useLocation, useNavigate, useParams} from "react-router";

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

const spriteJsonUrl = pathToFullUrl("/v1/content/sprites/simplified@2x.json")
const spritePngUrl = pathToFullUrl("/v1/content/sprites/simplified@2x.png")

function Endpoints() {
    return (
        <div>
            <h2>GET Endpoints</h2>
            <ol>
                {endPoints.map((each, i) => {
                    return (<li key={i}><a href={pathToFullUrl(each)}>{each}</a></li>)
                })}
            </ol>
        </div>
    )
}

function Charts() {
    const [charts, setCharts] = useState([])
    useRequest("/v1/chart_catalog", setCharts)

    return (
        <div>
            <Table striped bordered hover variant="light" className="w-50">
                <thead>
                <tr>
                    <th>Record ID</th>
                    <th>Chart Name</th>
                </tr>
                </thead>
                <tbody>
                {charts.map((each, i) => {
                    return (
                        <tr key={i}>
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

function imgStyle(value) {
    return {
        width: `${value.width}px`,
        height: `${value.height}px`,
        background: `url(${spritePngUrl})`,
        backgroundPosition: `-${value.x}px -${value.y}px`,
        display: 'inline-block',
        borderWidth: '4px'
    };
}

function Sprites() {
    const [themeData, setThemeData] = useState({})
    useRequest("/v1/content/sprites/simplified@2x.json", setThemeData)
    return (
        <div>
            <h2>Chart Symbol Sprites</h2>
            <div>
                Sprite sheet: <a href={spritePngUrl}>{spritePngUrl}</a>
            </div>
            <div>
                Sprite json: <a href={spriteJsonUrl}>{spriteJsonUrl}</a>
            </div>
            <div className="col">
                <div className="container">
                    <div className="row">
                        {Object.keys(themeData).map(key => {
                            return (
                                <div key={key} className="col-sm">
                                    <br/>
                                    {key}
                                    <div style={imgStyle(themeData[key])}/>
                                </div>
                            )
                        })}
                    </div>
                </div>
            </div>

        </div>
    )
}

function getTab(location) {
    return location.pathname.substring(location.pathname.lastIndexOf("/") + 1)
}

function ControlPanel() {
    const nav = useNavigate()
    const location = useLocation()
    useEffect(() => {
        setTabState(getTab(location))
    }, [location])
    let [tabState, setTabState] = useState(getTab(location))

    return (
        <div className="container-fluid Content">
            <h1>ControlPanel</h1>
            <Tabs
                id="controlled-tab-example"
                activeKey={`${tabState}`}
                onSelect={(k) => {
                    nav(`/control/${k}`)
                    setTabState(`${k}`)
                }}
                className="mb-3"
            >
                <Tab eventKey="charts" title="Charts">
                    <Charts/>
                </Tab>
                <Tab eventKey="symbols" title="Symbols">
                    <ChartSymbols/>
                </Tab>
                <Tab eventKey="sprites" title="Sprites">
                    <Sprites/>
                </Tab>
                <Tab eventKey="endpoints" title="Endpoints">
                    <Endpoints/>
                </Tab>
            </Tabs>
        </div>
    );
}

export default ControlPanel;
