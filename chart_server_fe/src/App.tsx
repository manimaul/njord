import njord from './njord.png'
import './App.css';
import {Outlet, Route, Routes} from "react-router-dom";
import React, {useState} from "react";
import {NavBar} from "./components/NavBar";
import {Enc} from "./components/Enc";
import {Table} from "react-bootstrap";
import {useRequest} from "./Effects";
import {ControlPanel} from "./components/ControlPanel";
import ChartInfo from "./components/Chartinfo";
import {LayerLocate} from "./components/LayerLocate";

function Home() {
    const [apiInfo, initVersion] = useState({
        version: "",
        gdalVersion: ""
    })

    useRequest("/v1/about/version", initVersion)

    return (
        <div className="container-fluid">
            <header className="Header">
                <img src={njord} className="img-fluid w-25" alt="logo"/>
            </header>
            <div className="Center">
                <Table striped bordered hover variant="light" className="w-50">
                    <thead>
                    <tr>
                        <th colSpan={2}>Njord Electronic Navigation Chart Server</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr>
                        <td>UI Version</td>
                        <td>{process.env.REACT_APP_VERSION}</td>
                    </tr>
                    <tr>
                        <td>API Version</td>
                        <td>{apiInfo.version}</td>
                    </tr>
                    <tr>
                        <td>Gdal Version</td>
                        <td>{apiInfo.gdalVersion}</td>
                    </tr>
                    </tbody>
                </Table>
            </div>
        </div>
    );
}

const NoMatch = () => <header className="App Header">
    <p>Page not found!</p>
</header>

export enum DepthUnit {
    feet = 'feet',
    fathoms = 'fathoms',
    meters = 'meters'
}

function depthUnit(): DepthUnit {
    switch (window.localStorage.getItem("depthUnit")) {
        case 'feet':
            return DepthUnit.feet
        case 'meters':
            return DepthUnit.meters
        case 'fathoms':
            return DepthUnit.fathoms
        default:
            return DepthUnit.meters
    }
}

function App() {
    const [depths, setDepthUnit] = useState(depthUnit());
    const depthUpdater = (d: DepthUnit) => {
        setDepthUnit(d);
        window.localStorage.setItem("depthUnit", d);
        console.log(`stored depth units= ${d}`)
    }

    return (
        <div className="App">
            <NavBar depths={depths} updater={depthUpdater}/>
            <div className="Warning bg-danger text-white">EXPERIMENTAL! - NOT FOR NAVIGATION</div>
            <Routes>
                <Route path="/" element={<Outlet/>}>
                    <Route index element={<Home/>}/>
                    <Route path="enc" element={<Enc depths={depths}/>}/>
                    <Route path="chart/:id" element={<ChartInfo/>}/>
                    <Route path="layer/:layer" element={<LayerLocate/>}/>
                    <Route path="control/:page" element={<ControlPanel/>}>
                        <Route path=":object" element={<ControlPanel/>}>
                            <Route path=":attribute" element={<ControlPanel/>}/>
                        </Route>
                    </Route>
                    <Route path="*" element={<NoMatch/>}/>
                </Route>
            </Routes>
        </div>
    );
}

export default App;
