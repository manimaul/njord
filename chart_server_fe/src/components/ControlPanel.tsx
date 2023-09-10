import React, {useEffect, useState} from "react";
import {Tab, Tabs} from "react-bootstrap";
import ChartSymbols from "./ControlSymbols";
import {useNavigate} from "react-router";
import {useParams} from 'react-router-dom';
import {Sprites} from "./Sprites";
import {Colors} from "./Colors";
import {ControlCharts} from "./ControlCharts";
import {ControlEndpoints} from "./ControlEndpoints";
import {ChartInstall} from "./ChartInstall";
import {useAdmin} from "../Admin";


export function ControlPanel() {
    const nav = useNavigate()
    let {page} = useParams();
    let {object} = useParams();
    let {attribute} = useParams();
    useEffect(() => {
        setTabState(page)
    }, [page])
    let [tabState, setTabState] = useState(page)
    let [admin] = useAdmin()

    return (
        <div className="container Content">
            <h1>ControlPanel</h1>
            <Tabs
                id="controlled-tab-example"
                activeKey={`${tabState}`}
                onSelect={(k) => {
                    nav(`/control/${k}`)
                    setTabState(`${k}`)
                }}
                className="mb-3">
                <Tab eventKey="charts_catalog" title="Chart Catalog">
                    <ControlCharts/>
                </Tab>
                <Tab eventKey="charts_installer" title="Chart Installer">
                    {admin && <ChartInstall/>}
                    {!admin && <p>Admin access required</p>}
                </Tab>
                <Tab eventKey="symbols" title="Symbols">
                    <ChartSymbols object={object} attribute={attribute}/>
                </Tab>
                <Tab eventKey="sprites" title="Sprites">
                    <Sprites/>
                </Tab>
                <Tab eventKey="colors" title="Colors">
                    <Colors/>
                </Tab>
                <Tab eventKey="endpoints" title="Endpoints">
                    <ControlEndpoints/>
                </Tab>
            </Tabs>
        </div>
    );
}
