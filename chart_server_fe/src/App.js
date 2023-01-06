import njord from './njord.png'
import './App.css';
import {Routes, Route, Outlet} from "react-router-dom";
import React from "react";

const Home = () => <header className="App-header">
    <img src={njord} className="App-logo" alt="logo"/>
    <p>
        Njord ENC - Electronic Navigation Chart Server
    </p>
</header>;
const About = () => <h1>About</h1>;
const NoMatch = () => <h2>Nothing to see here!</h2>

function App() {
    return (
        <div>
            <Routes>
                <Route path="/" element={<Outlet />}>
                    <Route index element={<Home />}/>
                    <Route path="about" element={<About />}/>
                    <Route path="*" element={<NoMatch/>}/>
                </Route>
            </Routes>
        </div>
    );
}
export default App;
