import React, {useRef, useState} from "react";
import Form from "react-bootstrap/Form";
import Button from "react-bootstrap/Button";
import {Admin, useAdmin} from "../Admin";
import Loading from "./Loading";
import {handleMessage, WsFatalError, WsInfo, WsInsertion, WsInsertionStatus, wsUri} from "../WsMsg";

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

function queryParams(upload: EncUpload, admin: Admin | null): string {
    let files = ""
    upload.files.forEach(ea => {
        files = files + `&file=${encodeURIComponent(ea)}`
    })
    return `?uuid=${encodeURIComponent(upload.uuid)}&signature=${encodeURIComponent(admin?.signatureEncoded ?? "")}${files}`
}

export function ChartInstall() {
    const [websocket, setWebsocket] = useState<WebSocket | null>(null)
    const [message, setMessage] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)
    const [upload, setUpload] = useState<EncUpload | null>(null)
    const [admin] = useAdmin()

    function wsFatalError(msg: WsFatalError) {
        console.log(`insertionError ${JSON.stringify(msg)}`)
    }

    function wsInfo(msg: WsInfo) {
        console.log(`insertionInfo ${JSON.stringify(msg)}`)
    }

    function wsInsertionStatus(msg: WsInsertionStatus) {
        console.log(`insertionStatus ${JSON.stringify(msg)}`)
    }

    function wsInsertion(msg: WsInsertion) {
        console.log(`insertion ${JSON.stringify(msg)}`)
    }

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
            setMessage("ERROR")
            setUpload(null)
        }
        ws.onopen = (event) => {
            console.log(`ws open ${JSON.stringify(event)}`)
            setWebsocket(ws)
        }
        ws.onmessage = (event) => {
            console.log(`ws message ${JSON.stringify(event)}`)
            handleMessage(event.data, wsFatalError, wsInfo, wsInsertionStatus, wsInsertion)
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
        {!loading && !websocket && <>
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
        {upload &&
            <p>upload uuid: {`${upload.uuid}`}</p>
        }
        {message &&
            <p> message: {`${message}`}</p>
        }
    </div>
}