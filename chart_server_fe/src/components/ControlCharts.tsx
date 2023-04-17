import React, {useState} from "react";
import {Link} from "react-router-dom";
import Button from "react-bootstrap/Button";
import Table from "react-bootstrap/Table";
import Form from "react-bootstrap/Form";
import useControlChartsViewModel from "../viewmodel/ControlChartsViewModel";
import Loading from "./Loading";
import {Popover, ProgressBar} from "react-bootstrap";
import OverlayTrigger from 'react-bootstrap/OverlayTrigger';

export function ControlCharts() {
    const [state, setFilter, deleteCharts, reload] = useControlChartsViewModel()
    const [showConfirm, setShowConfirm] = useState(false)

    const popover = (
        <Popover id="popover-basic">
            <Popover.Header as="h3">Are you sure?</Popover.Header>
            <Popover.Body>
                <>
                    <Button
                        variant="outline-danger"
                        size="sm" onClick={() => {
                        setShowConfirm(false)
                        deleteCharts()
                    }
                    }>Yes - delete them!</Button> <Button
                    variant="outline-success"
                    size="sm" onClick={() => {
                    setShowConfirm(false)
                }
                }>Cancel</Button>
                </>
            </Popover.Body>
        </Popover>
    );

    return (
        <div>
            {state.totalChartCount != null && <>
                <h2>{`Installed S57 ENCs: ${state.totalChartCount}`} </h2>
            </>}
            <Form.Control
                autoFocus
                className="my-2"
                placeholder="Type to filter name..."
                onChange={(e) => setFilter(e.target.value)}
                value={state.filter}
            />
            {state.admin &&
                <OverlayTrigger trigger="click" placement="right" overlay={popover} show={showConfirm}>
                    <Button variant="outline-danger" size="sm" onClick={() => setShowConfirm(true)}>Delete
                        all</Button>
                </OverlayTrigger>}{' '}
            {state.filter.length > 0 &&
                <>
                    <Button
                        variant="outline-success"
                        size="sm" onClick={() => {
                        setFilter("")
                    }}>Clear</Button>{' '}
                </>
            }
            {!state.loadingMore &&
                <Button variant="outline-secondary" size="sm" onClick={reload}>Reload</Button>
            }
            <br/>
            {state.deleteProgress >= 0 && <>
                <ProgressBar variant="success" min={0} max={1.0} now={state.deleteProgress}/>
            </>}
            <br/>
            <Table striped bordered hover variant="light">
                <thead>
                <tr>
                    <th>Record ID</th>
                    <th>Chart Name</th>
                </tr>
                </thead>
                <tbody>
                {state.charts.map((each, i) => {
                    return (
                        <tr key={i}>
                            <td>{each.id}</td>
                            <td><Link to={`/chart/${each.id}`}>{each.name}</Link></td>
                        </tr>
                    )
                })}
                </tbody>
            </Table>
            {state.loadingMore && <Loading/>}
        </div>
    )
}