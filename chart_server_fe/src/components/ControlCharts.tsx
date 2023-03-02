import React, {useState} from "react";
import {useRequest} from "../Effects";
import {Table} from "react-bootstrap";
import {Link} from "react-router-dom";

type ChartProps = {
    id: number;
    name: string;
};

export function ControlCharts() {
    const [charts, setCharts] = useState<Array<ChartProps>>([])
    useRequest("/v1/chart_catalog", setCharts)

    return (
        <div>
            <h2>Installed S57 ENCs</h2>
            <Table striped bordered hover variant="light">
                <thead>
                <tr>
                    <th>Record ID</th>
                    <th>Chart Name</th>
                </tr>
                </thead>
                <tbody>
                {charts.map((each, i) => {
                    return (
                        <tr key={i}>
                            <td>{each.id}</td>
                            <td>
                                <Link to={`/chart/${each.id}`} >{each.name}</Link>
                            </td>
                        </tr>
                    )
                })}
                </tbody>
            </Table>
        </div>
    )
}