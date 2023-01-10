import React, {useEffect, useState} from "react";
import {Tab, Table, Tabs} from "react-bootstrap";
import ChartSymbols from "./ControlSymbols";
import {useLocation, useNavigate} from "react-router";
import {Sprites} from "./Sprites";
import {ControlCharts} from "./ControlCharts";
import {ControlEndpoints} from "./ControlEndpoints";


function getTab(location: any) {
    return location.pathname.substring(location.pathname.lastIndexOf("/") + 1)
}

export function ControlPanel() {
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
                    <ControlCharts/>
                </Tab>
                <Tab eventKey="symbols" title="Symbols">
                    <ChartSymbols/>
                </Tab>
                <Tab eventKey="sprites" title="Sprites">
                    <Sprites/>
                </Tab>
                <Tab eventKey="endpoints" title="Endpoints">
                    <ControlEndpoints/>
                </Tab>
            </Tabs>
        </div>
    );
}
