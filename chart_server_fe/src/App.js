import njord from './njord.png'
import './App.css';
import {Routes, Route, Outlet} from "react-router-dom";
import React from "react";
import NavBar from "./NavBar";
import Enc from "./Enc";
import ControlPanel from "./ControlPanel";

const Home = () => <header className="App Header">
    <img src={njord} className="img-fluid w-50" alt="logo"/>
    <p>Njord ENC Server</p>
</header>;
const NoMatch = () => <header className="App Header">
    <p>Page not found!</p>
</header>

function App() {
    return (
        <div className="App">
            <NavBar/>
            <Routes>
                <Route path="/" element={<Outlet/>}>
                    <Route index element={<Home/>}/>
                    <Route path="enc" element={<Enc/>}/>
                    <Route path="control" element={<ControlPanel/>}/>
                    <Route path="*" element={<NoMatch/>}/>
                </Route>
            </Routes>
        </div>
    );
}

export default App;
