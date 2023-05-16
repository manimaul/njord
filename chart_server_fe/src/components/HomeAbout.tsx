import React, {useEffect, useState} from "react";
import {useRequest} from "../Effects";
import njord from "../njord.jpg";
import {Table} from "react-bootstrap";
import Footer from "./Footer";
import "../App.css"
import {useAdmin} from "../Admin";

export default function HomeAbout() {
    const [apiInfo, initVersion] = useState({
        version: "",
        gdalVersion: "",
        gitHash: "",
        gitBranch: "",
        buildDate: "",
    })

    useRequest("/v1/about/version", initVersion)
    const [admin, , , validate] = useAdmin();
    const [checked, setChecked] = useState(false)

    useEffect(() => {
        if (!checked && admin) {
            console.log("validating admin")
            validate()
            setChecked(true)
        }
    }, [admin, validate, checked, setChecked])

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
                            <td>Njord Version</td>
                            <td>
                                {apiInfo.version}
                            </td>
                        </tr>
                        <tr>
                            <td>Git Commit</td>
                            <td>
                                <a href={`https://github.com/manimaul/njord/commit/${apiInfo.gitHash}`}>{apiInfo.gitHash}</a>
                            </td>
                        </tr>
                        <tr>
                            <td>Gdal Version</td>
                            <td>{apiInfo.gdalVersion}</td>
                        </tr>
                        {checked && admin &&
                            <tr>
                                <td>Admin</td>
                                <td>
                                    valid until: {admin.signature.expirationDate}
                                </td>
                            </tr>
                        }
                        </tbody>
                    </Table>
                </div>
            </div>
            <Footer />
        </div>
    );
}

