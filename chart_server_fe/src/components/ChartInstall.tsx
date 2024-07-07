import React, {useRef, useState} from "react";
import Form from "react-bootstrap/Form";
import Button from "react-bootstrap/Button";
import {InsertItem, WsCompletionReport} from "../WsMsg";
import useChartInstallViewModel from "../viewmodel/ChartInstallViewModel";
import {ProgressBar} from "react-bootstrap";

type ChartFormProps = {
    onSubmit: (data: FormData) => void
}

type ChartFormUrlProps = {
    onSubmit: (url: String) => void
}
function ChartInstallFormUrl(props: ChartFormUrlProps) {
    const [url, setUrl] = useState("")
    return <>
        <Form>
            <Form.Group className="mb-3" controlId="encUrl">
                <Form.Label>ENC Chart Zip File Url</Form.Label>
                <Form.Control type="text" size="sm" onChange={(e) => {
                    console.log(e.target.value)
                    setUrl(e.target.value)
                }} />
            </Form.Group>
            <Button variant="primary" type="submit" onClick={(e) => {
                e.preventDefault()
                props.onSubmit(url)
            }}>
                Submit
            </Button>
        </Form>
    </>
}
function ChartInstallForm(props: ChartFormProps) {
    const formElem = useRef(null)
    return <>
        <Form ref={formElem}>
            <Form.Group className="mb-3" controlId="encZip">
                <Form.Label>ENC Chart Zip File</Form.Label>
                <Form.Control type="file" name="enczip" accept="application/zip"/>
            </Form.Group>
            <Button variant="primary" type="submit" onClick={(e) => {
                e.preventDefault()
                let data = new FormData(formElem.current!)
                props.onSubmit(data)
            }}>
                Submit
            </Button>
        </Form>
    </>
}

export function ChartInstall() {
    const [state, upload, uploadUrl, reload] = useChartInstallViewModel()

    return <div className="container Content">
        {!(state.uploadProgress >= 0) && state.admin && <>
            <ChartInstallForm onSubmit={(data) => {
                console.log("submitted")
                upload(data)
            }
            }/>
            <ChartInstallFormUrl onSubmit={(url) => {
                console.log(`submitted ${url}`)
                uploadUrl(url)
            }
            }/>
        </>
        }
        {!state.admin &&
            <>
                <p>Admin access required!</p>
            </>
        }
        {state.uploadProgress >= 0 &&
            <>
                <ProgressBar variant="success" min={0} max={1.0} now={state.uploadProgress} />
                <p>Uploading file...</p>
            </>
        }
        {state.extractProgress >= 0 &&
            <>
                <ProgressBar variant="success" min={0} max={1.0} now={state.extractProgress} />
                <p>Extracting file(s)...</p>
            </>
        }
        {state.installProgress >= 0 &&
            <>
                <ProgressBar variant="success" min={0} max={1.0} now={state.installProgress} />
                <p>Installing ENC geometries...</p>
            </>
        }
        {state.info &&
            <>
                <p>S57 {state.info.num} of {state.info.total}</p>
                <p>{state.info.message}</p>
            </>
        }
        {state.report &&
            <>
               <Button variant="outline-secondary" size="sm" onClick={reload}>Reload</Button>
               <ChartTable report={state.report}  />
            </>
        }
    </div>
}

type ChartTableProps = {
    report: WsCompletionReport
}

function ChartTable(props: ChartTableProps) {
    let items = new Array<Array<InsertItem>>()
    let rows = 6
    for (let i = 0; i < props.report.items.length; i += rows) {
        items.push(props.report.items.slice(i, i + rows));
    }

    return <>
        <h4>Total Charts Installed {props.report.totalChartCount}</h4>
        <p>Total Features Installed {props.report.totalFeatureCount}</p>
        <p>Total Elapsed Time (Seconds) {props.report.ms / 1000}</p>
        {
            (props.report.failedCharts.length > 0) && <>
                <h1>Failed Charts</h1>
                {props.report.failedCharts.map(name => <p>{name}</p>)}
            </>
        }
        {
            items.map((arr, i) => <div key={i} className="row">
                {arr.map((item, ii) => <div key={ii} className="col">
                    <h6>{item.chartName}</h6>
                    <p>feature count: {item.featureCount}</p>
                </div>)}
            </div>)
        }
    </>
}