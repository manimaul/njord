import njord from './njord.png'
import './App.css';
import {Routes, Route, Outlet} from "react-router-dom";
import React, {useCallback, useEffect, useState} from "react";
import NavBar from "./NavBar";
import Enc from "./Enc";
import ControlPanel from "./ControlPanel";
import {Table} from "react-bootstrap";

function Home() {
    const [apiInfo, initVersion] = useState({
        version: "",
        gdalVersion: ""
    })

    const fetchVersionInfo = useCallback(async () => {
        let response = await fetch("/v1/about/version")
        response = await response.json()
        initVersion(response)
    }, [])

    useEffect(() => {
        fetchVersionInfo()
    }, [fetchVersionInfo])

    return (
        <div className="container-fluid">
            <header className="Header">
                <img src={njord} className="img-fluid w-25" alt="logo"/>

            </header>
            <div className="Center">
                <Table striped bordered hover variant="dark" className="w-50">
                    <thead>
                    <tr>
                        <th colSpan="2">Njord ENC Server</th>
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
