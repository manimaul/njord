import React, {useState} from "react";
import {useRequest} from "../Effects";
import {Table} from "react-bootstrap";

type ChartProps = {
    id: number;
    name: string;
};

export function ControlCharts() {
    const [charts, setCharts] = useState<Array<ChartProps>>([])
    useRequest("/v1/chart_catalog", setCharts)

    return (
        <div>
            <Table striped bordered hover variant="light" className="w-50">
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
                            <td>{each.name}</td>
                        </tr>
                    )
                })}
                </tbody>
            </Table>
        </div>
    )
}