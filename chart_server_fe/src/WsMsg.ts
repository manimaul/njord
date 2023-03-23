import {Admin} from "./Admin";
import {EncUpload} from "./viewmodel/ChartInstallViewModel";

export type WsError = {
    message: string
    isFatal: boolean
}

export type WsExtracting = {
    step: number
    steps: number
    progress: number
}

export type WsInfo = {
    num: number
    total: number
    message: string
}


export type WsCompletionReport = {
    totalFeatureCount: number
    totalChartCount: number
    items: Array<InsertItem>
    failedCharts: Array<string>
    ms: number
}

export type InsertItem = {
    chartName: string
    featureCount: number
}

export function handleMessage(
    msg: string,
    error: (arg: WsError) => void,
    info: (arg: WsInfo) => void,
    extracting: (arg: WsExtracting) => void,
    completion: (arg: WsCompletionReport) => void,
) {
    let m = JSON.parse(msg)
    let t = m["type"]
    switch (t) {
        case "Error":
            error(m as WsError)
            break;
        case "Info":
            info(m as WsInfo)
            break;
        case "CompletionReport":
            completion(m as WsCompletionReport)
            break;
        case "Extracting":
            extracting(m as WsExtracting)
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
