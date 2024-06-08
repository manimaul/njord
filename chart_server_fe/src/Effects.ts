import {useEffect, useState} from "react";

export async function fetchData(path: string, callback: (arg: any) => void) {
    let response = await fetch(path)
    response = await response.json()
    callback(response)
}

export function useRequest(path: string, callback: (arg: any) => void) : [() => void] {
    const [run, setRun] = useState(false)

    useEffect(() => {
        if (!run) {
            fetchData(path, callback)
            setRun(true)
        }
    }, [run, callback, path])
    return [function () {
        setRun(false)
    }]
}

export function useTypeRequest<T>(path: string, callback: (arg: T) => void) {
    const [run, setRun] = useState(false)

    useEffect(() => {
        if (!run) {
            fetchData(path, (arg) => {
            	callback(JSON.parse(arg));
				});
            setRun(true)
        }
    }, [run, callback, path])
}

export function useRequests(paths: Array<string>, callback: (arg: Array<any>) => void) {
    const [run, setRun] = useState(false)
    let responses: Array<any> = paths.map(_ => null);
	useEffect(() => {
		if (!run) {
			var count = 0;
			paths.forEach(async (path, i) => {
				let response = await fetch(path);
				response = await response.json();
				responses[i] = response;
				count = count + 1;
				if (count === paths.length) {
					setRun(true);
					callback(responses);
				}
			});
		}
    }, [run, callback, paths])
}

