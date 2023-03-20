import React, {useState} from "react";
import {useRequest} from "../Effects";
import {Table} from "react-bootstrap";
import {Link} from "react-router-dom";
import Button from "react-bootstrap/Button";
import {useAdmin} from "./Admin";
import Form from "react-bootstrap/Form";

type ChartProps = {
    id: number;
    name: string;
};

export type Bounds = {
    leftLng: number;
    topLat: number;
    rightLng: number;
    bottomLat: number;
}

export function ControlCharts() {
    const [filter, setFilter] = useState("")
    const [charts, setCharts] = useState<Array<ChartProps>>([])
    const [reload] = useRequest("/v1/chart_catalog", setCharts)
    const [admin] = useAdmin()

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
                <Form.Control
                    autoFocus
                    className="my-2"
                    placeholder="Type to filter name..."
                    onChange={(e) => setFilter(e.target.value)}
                    value={filter}
                />
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
                    <th>
                        Chart Name
                    </th>
                </tr>
                </thead>
                <tbody>
                {filteredCharts().map((each, i) => {
                    return (
                        <tr key={i}>
                            <td>{each.id}</td>
                            <td>
                                <Link to={`/chart/${each.id}`}>{each.name}</Link>{' '}
                                {admin &&
                                    <Button
                                        variant="outline-danger"
                                        size="sm" onClick={() => {
                                        deleteChart([each.id])
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