import React, {useRef, useState} from "react";
import Form from "react-bootstrap/Form";
import Button from "react-bootstrap/Button";
import {useAdmin} from "../Admin";
import Loading from "./Loading";
import {
    handleMessage, InsertItem, WsCompletionReport, WsError,
    WsInfo,
    wsUri
} from "../WsMsg";
import {ProgressBar} from "react-bootstrap";

type ChartFormProps = {
    onSubmit: (data: FormData) => void
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

export type EncUpload = {
    files: string[],
    uuid: string
}

export function ChartInstall() {
    const [websocket, setWebsocket] = useState<WebSocket | null>(null)
    const [info, setInfo] = useState<WsInfo | null>(null)
    const [error, setError] = useState<WsError | null>(null)
    const [loading, setLoading] = useState(false)
    const [upload, setUpload] = useState<EncUpload | null>(null)
    const [admin] = useAdmin()
    const [completion, setCompletion] = useState<WsCompletionReport | null>(null)
    const [progress, setProgress] = useState<number>(-1)

    function setupWs(eu: EncUpload) {
        let ws = new WebSocket(wsUri(eu, admin))
        ws.onclose = (event) => {
            console.log(`ws close ${JSON.stringify(event)}`)
            setWebsocket(null)
            setUpload(null)
        }
        ws.onerror = (event) => {
            console.log(`ws error ${JSON.stringify(event)}`)
            setWebsocket(null)
            setUpload(null)
        }
        ws.onopen = (event) => {
            console.log(`ws open ${JSON.stringify(event)}`)
            setWebsocket(ws)
        }
        ws.onmessage = (event) => {
            console.log(`ws message ${JSON.stringify(event)}`)
            handleMessage(event.data, setError, (inf) => {
                let pct = Math.round((inf.num / inf.total) * 100)
                setProgress(pct)
                setInfo(inf)
            }, (completion) => {
                setProgress(100)
                setCompletion(completion)
            })
        }
    }

    async function upLoad(data: FormData) {
        let response = await fetch(`/v1/enc_save?signature=${admin?.signatureEncoded}`, {
            method: 'POST',
            body: data
        });
        try {
            let encUpload: EncUpload = JSON.parse(await response.text())
            setUpload(encUpload)
            setupWs(encUpload)
        } catch (e) {
            console.log("error uploading data")
        }
        setLoading(false)
    }

    return <div className="container Content">
        <h2>Install S57 ENCs</h2>
        {!loading && !websocket && !completion && <>
            <ChartInstallForm onSubmit={(data) => {
                setLoading(true)
                console.log("submitted")
                upLoad(data)
            }
            }/>
        </>
        }
        {loading &&
            <Loading/>
        }
        {progress >= 0 &&
            <>
                <ProgressBar variant="success" now={progress}
                             label={`${progress}%`}/>
            </>
        }
        {
            error && <>
                <h5>Error: {error.message}</h5>
            </>
        }
        {upload && progress < 0 &&
            <>
                <h4>Unzipping...</h4>
                <p>upload uuid: {`${upload.uuid}`}</p>
            </>
        }
        {upload && progress > 0 &&
            <p>upload uuid: {`${upload.uuid}`}</p>
        }
        {info && !completion &&
            <>
                <p>S57 {info.num} of {info.total}</p>
                <p>{info.name}</p>
                <p>features: {info.featureCount}</p>
                <p>layer: {info.layer}</p>
            </>
        }
        {
            !loading && completion && <ChartTable report={completion}/>
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
                <h5>Failed Charts</h5>
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