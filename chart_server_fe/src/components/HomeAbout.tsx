import React, {useState} from "react";
import {useRequest} from "../Effects";
import njord from "../njord.png";
import {Table} from "react-bootstrap";
import Footer from "./Footer";
import "../App.css"

export default function HomeAbout() {
    const [apiInfo, initVersion] = useState({
        version: "",
        gdalVersion: ""
    })

    useRequest("/v1/about/version", initVersion)

    return (
        <div className="Column Fill">
            <div className="container Fill">
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
            <Footer />
        </div>
    );
}

