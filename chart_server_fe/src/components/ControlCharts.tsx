import React, {useState} from "react";
import {useRequest} from "../Effects";
import {Table} from "react-bootstrap";
import {Link, useNavigate} from "react-router-dom";
import Button from "react-bootstrap/Button";
import {useAdmin} from "../Admin";
import Form from "react-bootstrap/Form";

type ChartProps = {
    id: number;
    name: string;
    featureCount: number;
};

export function ControlCharts() {
    const [filter, setFilter] = useState("")
    const [charts, setCharts] = useState<Array<ChartProps>>([])
    const [reload] = useRequest("/v1/chart_catalog", setCharts)
    const [admin] = useAdmin()
    let navigate = useNavigate();

    async function deleteChart(ids: number[]) {
        setCharts([])
        await Promise.all(ids.map(id => {
            return fetch(
                `/v1/chart?id=${id}&signature=${admin?.signatureEncoded}`,
                {
                    method: "DELETE"
                }
            ).then(response => {
                console.log(`delete chart ${id} = ${response.statusText}`)
            })
        }))
        reload()
    }

    function filteredCharts(): ChartProps[] {
        return charts.filter(each => {
            if (filter.length > 0) {
                return each.name.includes(filter)
            }
            return true
        })
    }

    return (
        <div>
            <h2>Installed S57 ENCs</h2>
            {admin &&
                <>
                    <Button variant="outline-success" onClick={() => {
                        navigate('/chart/install')
                    }}>Install charts
                    </Button>
                    <Form.Control
                        autoFocus
                        className="my-2"
                        placeholder="Type to filter name..."
                        onChange={(e) => setFilter(e.target.value)}
                        value={filter}
                    />
                </>
            }
            {
                admin && filter.length > 0 &&
                <>
                    <Button
                        variant="outline-danger"
                        size="sm" onClick={() => {
                        let toDelete = filteredCharts().map(each => each.id)
                        deleteChart(toDelete)
                    }
                    }>Delete All
                    </Button>
                    <br/>
                    <br/>
                </>
            }
            <Table striped bordered hover variant="light">
                <thead>
                <tr>
                    <th>Record ID</th>
                    <th>Chart Name</th>
                    <th>Geometry Count</th>
                </tr>
                </thead>
                <tbody>
                {filteredCharts().map((each, i) => {
                    return (
                        <tr key={i}>
                            <td>{each.id}</td>
                            <td><Link to={`/chart/${each.id}`}>{each.name}</Link></td>
                            <td>{each.featureCount}</td>
                        </tr>
                    )
                })}
                </tbody>
            </Table>
        </div>
    )
}