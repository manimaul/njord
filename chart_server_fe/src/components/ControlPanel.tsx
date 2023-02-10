import {useEffect, useState} from "react";
import {Tab, Tabs} from "react-bootstrap";
import ChartSymbols from "./ControlSymbols";
import {useNavigate} from "react-router";
import {useParams} from 'react-router-dom';
import {Sprites} from "./Sprites";
import {ControlCharts} from "./ControlCharts";
import {ControlEndpoints} from "./ControlEndpoints";


export function ControlPanel() {
    const nav = useNavigate()
    let {page} = useParams();
    let {object} = useParams();
    let {attribute} = useParams();
    useEffect(() => {
        setTabState(page)
    }, [page])
    let [tabState, setTabState] = useState(page)

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
                <Tab eventKey="charts" title="Charts">
                    <ControlCharts/>
                </Tab>
                <Tab eventKey="symbols" title="Symbols">
                    <ChartSymbols object={object} attribute={attribute}/>
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
