import njord from './njord.png'
import './App.css';
import {Routes, Route, Outlet} from "react-router-dom";
import React, {useState} from "react";
import {NavBar} from "./components/NavBar";
import {Enc} from "./components/Enc";
import {Table} from "react-bootstrap";
import {useRequest} from "./Effects";
import {ControlPanel} from "./components/ControlPanel";

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

function App() {
    return (
        <div className="App">
            <NavBar/>
            <Routes>
                <Route path="/" element={<Outlet/>}>
                    <Route index element={<Home/>}/>
                    <Route path="enc" element={<Enc/>}/>
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
