import {useEffect, useState} from "react";

async function fetchData(path: string, callback: (arg: any) => void) {
    let response = await fetch(path)
    response = await response.json()
    callback(response)
}
export function useRequest(path: string, callback: (arg: any) => void) {
    const [run, setRun] = useState(false)

    useEffect(() => {
        if (!run) {
            fetchData(path, callback)
            setRun(true)
        }
    }, [run, callback, path])
}
