import {useEffect, useState} from "react";

async function fetchData<T>(path: string, callback: (arg: T) => void) {
    let response = await fetch(path)
    response = await response.json()
    callback(response as T)
}
export function useRequest<T>(path: string, callback: (arg: T) => void) {
    const [run, setRun] = useState(false)

    useEffect(() => {
        if (!run) {
            fetchData(path, callback)
            setRun(true)
        }
    }, [run, callback, path])
}
