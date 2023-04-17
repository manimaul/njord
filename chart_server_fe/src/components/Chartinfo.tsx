import {Link, useNavigate, useParams} from "react-router-dom";
import {useRequest} from "../Effects";
import React, {useState} from "react";
import Loading from "./Loading";
import {Table} from "react-bootstrap";
import {setDestination} from "./Enc";
import Button from "react-bootstrap/Button";

export type ChartInfoResponse = {
    id: number;
    name: string,
    file_name: string;
    updated: string;
    issued: string;
    zoom: number;
    scale: number;
    covr: any;
    bounds: Bounds,
    layers: Array<string>;
    chart_txt: Record<string, string>;
    dsid_props: Record<string, string | number>;
    featureCount: number;
}

export type Bounds = {
    leftLng: number;
    topLat: number;
    rightLng: number;
    bottomLat: number;
}
export default function ChartInfo() {
    let {id} = useParams();
    const [chartInfo, setChartInfo] = useState<ChartInfoResponse | null>(null)
    useRequest(`/v1/chart?id=${id}`, setChartInfo)
    let navigate = useNavigate();
    if (chartInfo) {
        return (
            <div className="container Content">
                <Table striped bordered hover variant="light">
                    <thead>
                    <tr>
                        <th>Name</th>
                        <th>Value</th>
                    </tr>
                    </thead>
                    <tbody>
                    {
                        <>
                            <tr>
                               <td>ID</td>
                                <td>{chartInfo.id}</td>
                            </tr>
                            <tr>
                                <td>Name</td>
                                <td>
                                    {chartInfo.name}{' '}
                                    <Button variant="outline-primary" size="sm"
                                            onClick={() => {
                                                setDestination(chartInfo.bounds)
                                                navigate("/enc")
                                            }}
                                    >ENC Zoom to</Button>
                                </td>
                            </tr>
                            <tr>
                                <td>Feature Count</td>
                                <td>{chartInfo.featureCount}</td>
                            </tr>
                            <tr>
                                <td>Updated</td>
                                <td>{chartInfo.updated}</td>
                            </tr>
                            <tr>
                                <td>Issued</td>
                                <td>{chartInfo.issued}</td>
                            </tr>
                            <tr>
                                <td>Zoom</td>
                                <td>{chartInfo.zoom}</td>
                            </tr>
                            <tr>
                                <td>Scale</td>
                                <td>{chartInfo.scale}</td>
                            </tr>
                            <tr>
                                <td>Layers</td>
                                <td>{chartInfo.layers.map((each, i) => {
                                    return <><Link key={`${i}`} to={`/control/symbols/${each}`}>{each}</Link> </>
                                })}</td>
                            </tr>
                            {Object.keys(chartInfo.chart_txt).map((each, i) => {
                                return <tr key={i}>
                                    <td>{each}</td>
                                    <td><p>{chartInfo?.chart_txt?.[each] ?? ""}</p></td>
                                </tr>
                            })
                            }
                            {Object.keys(chartInfo.dsid_props).map((each, i) => {
                                return <tr key={i}>
                                    <td>{each}</td>
                                    <td><p>{chartInfo?.dsid_props?.[each] ?? ""}</p></td>
                                </tr>
                            })
                            }
                        </>
                    }
                    </tbody>
                </Table>
            </div>
        )
    } else {
        return (
            <Loading/>
        )
    }
}