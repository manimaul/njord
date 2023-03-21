import './App.css';
import {Outlet, Route, Routes} from "react-router-dom";
import React, {useState} from "react";
import {NavBar} from "./components/NavBar";
import {Enc} from "./components/Enc";
import {ControlPanel} from "./components/ControlPanel";
import ChartInfo from "./components/Chartinfo";
import {LayerLocate} from "./components/LayerLocate";
import HomeAbout from "./components/HomeAbout";
import {ChartInstall} from "./components/ChartInstall";

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
        <div className="App Column">
            <NavBar depths={depths} updater={depthUpdater}/>
            <div className="Wrap Warning bg-danger text-white">EXPERIMENTAL! - NOT FOR NAVIGATION</div>
            <Routes>
                <Route path="/" element={<Outlet/>}>
                    <Route index element={<HomeAbout/>}/>
                    <Route path="enc" element={<Enc depths={depths}/>}/>
                    <Route path="chart/:id" element={<ChartInfo/>}/>
                    <Route path="chart/install" element={<ChartInstall/>}/>
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
