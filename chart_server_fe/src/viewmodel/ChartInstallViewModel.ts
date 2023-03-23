import {useEffect, useState} from "react";
import {handleMessage, WsCompletionReport, WsError, WsInfo, wsUri} from "../WsMsg";
import {Admin, useAdmin} from "../Admin";

export type EncUpload = {
    files: string[],
    uuid: string
}

export type ChartInstallState = {
    uploadProgress: number,
    extractProgress: number,
    installProgress: number,
    report: WsCompletionReport | null,
    info: WsInfo | null,
    admin: Admin | null,
    err: WsError | null,
}
export default function useChartInstallViewModel(): [ChartInstallState, (upload: FormData) => void, () => void] {
    const [admin] = useAdmin()
    const [state, setState] = useState<ChartInstallState>(defaultState())

    function defaultState(): ChartInstallState {
        return {
            uploadProgress: -1,
            extractProgress: -1,
            installProgress: -1,
            report: null,
            info: null,
            admin: admin,
            err: null,
        }
    }

    useEffect(() => {
        setState(old => {
            return {...old, admin: admin}
        })
    }, [admin])

    const [websocket, setWebsocket] = useState<WebSocket | null>(null)

    function setError(e: WsError | null) {
        setState(old => {
            return {...old, err: e}
        })
    }

    function setInfo(i: WsInfo) {
        let progress = i.num / i.total
        setState(old => {
            return {...old, info: i, installProgress: Math.max(old.installProgress, progress)}
        })
    }

    function setCompletion(c: WsCompletionReport) {
        setState(old => {
            return {...old, info: null, installProgress: 1, report: c}
        })
    }

    function setupWs(eu: EncUpload) {
        let ws = new WebSocket(wsUri(eu, admin))
        ws.onclose = (event) => {
            console.log(`ws close ${JSON.stringify(event)}`)
            setWebsocket(null)
        }
        ws.onerror = (event) => {
            console.log(`ws error ${JSON.stringify(event)}`)
            setWebsocket(null)
        }
        ws.onopen = (event) => {
            console.log(`ws open ${JSON.stringify(event)}`)
            setWebsocket(ws)
        }
        ws.onmessage = (event) => {
            console.log(`ws message ${JSON.stringify(event)}`)
            handleMessage(event.data, setError, (inf) => {
                setInfo(inf)
            }, (extracting) => {
                let p: number
                if (extracting.step === 1) {
                    p = extracting.progress / extracting.steps
                } else {
                    p = extracting.progress + (extracting.step - 1 / extracting.steps)
                }
                setState(old => {
                    return {...old, extractProgress: p}
                })
            }, (completion) => {
                setCompletion(completion)
            })
        }
    }

    function uploadData(data: FormData) {
        setState(old => {
            return {...old, uploadProgress: 0, extractProgress: 0, installProgress: 0}
        })
        const xhr = new XMLHttpRequest();
        xhr.upload.addEventListener('progress', e => {
            setState(old => {
                return {...old, uploadProgress: e.loaded / e.total}
            })
        });
        xhr.addEventListener('load', () => {
            let encUpload: EncUpload = JSON.parse(xhr.responseText)
            setupWs(encUpload)
        })
        let url = `/v1/enc_save?signature=${admin?.signatureEncoded}`
        xhr.open('POST', url, true);
        xhr.send(data);
    }

    function reload() {
        websocket?.close()
        setState(defaultState())
    }

    return [state, uploadData, reload]
}
