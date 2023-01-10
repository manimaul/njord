
export function pathToFullUrl(path: string) {
    return `${window.location.protocol}//${window.location.host}${path}`
}
