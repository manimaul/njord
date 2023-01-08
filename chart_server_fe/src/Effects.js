import {useEffect, useState} from "react";

async function fetchData(path, callback) {
    let response = await fetch(path)
    response = await response.json()
    callback(response)
}
export function useRequest(path, callback) {
    const [run, setRun] = useState(false)

    useEffect(() => {
        if (!run) {
            fetchData(path, callback)
            setRun(true)
        }
    }, [run, callback, path])
}
