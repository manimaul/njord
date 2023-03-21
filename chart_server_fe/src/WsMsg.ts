import {Admin} from "./Admin";
import {EncUpload} from "./components/ChartInstall";

export type WsFatalError = {
    message: string
}

export type WsInfo = {
    message: string
}

export type WsInsertionStatus = {
    chartName: string
    message: string
    isError: boolean
}

export type WsInsertion = {
    chartName: string
    message: string
    isError: boolean
}

export function handleMessage(
    msg: string,
    fatal: (arg: WsFatalError) => void,
    info: (arg: WsInfo) => void,
    insStatus: (arg: WsInsertionStatus) => void,
    insertion: (arg: WsInsertion) => void,
) {
    let m = JSON.parse(msg)
    let t = m["type"]
    switch (t) {
        case "FatalError":
            fatal(m as WsFatalError)
            break;
        case "Info":
            info(m as WsInfo)
            break;
        case "InsertionStatus":
            insStatus(m as WsInsertionStatus)
            break;
        case "Insertion":
            insertion(m as WsInsertion)
            break;
    }
}

function queryParams(upload: EncUpload, admin: Admin | null): string {
    let files = ""
    upload.files.forEach(ea => {
        files = files + `&file=${encodeURIComponent(ea)}`
    })
    return `?uuid=${encodeURIComponent(upload.uuid)}&signature=${encodeURIComponent(admin?.signatureEncoded ?? "")}${files}`
}
export function wsUri(upload: EncUpload, admin: Admin | null): string {
    var loc = window.location.host
    if (loc.endsWith(":3000")) {
        loc = window.location.hostname + ":9000"
    }
    let baseUri = (window.location.protocol === 'https:' && 'wss://' || 'ws://') + loc;
    return `${baseUri}/v1/ws/enc_process${queryParams(upload, admin)}`
}
