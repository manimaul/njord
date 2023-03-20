import React, {useState} from "react";
import {useRequest} from "../Effects";
import {Table} from "react-bootstrap";
import {Link, useNavigate} from "react-router-dom";
import Button from "react-bootstrap/Button";
import {setDestination, storeEncState} from "./Enc";
import {useAdmin} from "./Admin";

type ChartProps = {
    id: number;
    name: string;
    bounds: Bounds;
};

export type Bounds = {
    leftLng: number;
    topLat: number;
    rightLng: number;
    bottomLat: number;
}

export function ControlCharts() {
    const [charts, setCharts] = useState<Array<ChartProps>>([])
    const [reload] = useRequest("/v1/chart_catalog", setCharts)
    const [admin] = useAdmin()

    async function deleleteChart(id: number) {
        setCharts([])
        let response = await fetch(
            `/v1/chart?id=${id}&signature=${admin?.signatureEncoded}`,
            {
                method: "DELETE"
            }
        )
        console.log(`delete chart ${id} = ${response.statusText}`)
        reload()
    }

    return (
        <div>
            <h2>Installed S57 ENCs</h2>
            <Table striped bordered hover variant="light">
                <thead>
                <tr>
                    <th>Record ID</th>
                    <th>
                        Chart Name
                    </th>
                </tr>
                </thead>
                <tbody>
                {charts.map((each, i) => {
                    return (
                        <tr key={i}>
                            <td>{each.id}</td>
                            <td>
                                <Link to={`/chart/${each.id}`}>{each.name}</Link>{' '}
                                {admin &&
                                    <Button
                                        variant="outline-danger"
                                        size="sm" onClick={() => {
                                        deleleteChart(each.id)
                                    }
                                    }>Delete
                                    </Button>
                                }
                            </td>
                        </tr>
                    )
                })}
                </tbody>
            </Table>
        </div>
    )
}