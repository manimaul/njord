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

export enum Theme {
    day = 'day',
    dusk = 'dusk',
    night = 'night'
}

function getTheme(): Theme {
    switch (window.localStorage.getItem("theme")) {
        case 'day':
            return Theme.day 
        case 'dusk':
            return Theme.dusk 
        case 'night':
            return Theme.night
        default:
            return Theme.day 
    }
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
    const [theme, setTheme] = useState(getTheme());
    const themeUpdater = (t: Theme) => {
        setTheme(t);
        window.localStorage.setItem("theme", t);
        console.log(`stored theme= ${t}`)
    }
    const [custom, setCustom] = useState(window.localStorage.getItem("custom"));
    const customUpdater = (c: string) => {
        setCustom(c)
        window.localStorage.setItem("custom", c);
        console.log(`stored custom= ${c}`)
    }

    return (
        <div className="App Column">
            <NavBar theme={theme} depths={depths} custom={custom} depthsUpdater={depthUpdater} themeUpdater={themeUpdater} customUpdater={customUpdater}/>
            <div className="Wrap Warning bg-danger text-white">EXPERIMENTAL! - NOT FOR NAVIGATION</div>
            <Routes>
                <Route path="/" element={<Outlet/>}>
                    <Route index element={<HomeAbout/>}/>
                    <Route path="enc" element={<Enc depths={depths} theme={theme} custom={custom}/>}/>
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
