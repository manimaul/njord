import {useParams} from "react-router-dom";
import {useNavigate} from "react-router-dom";
import {useEffect, useState} from "react";
import {useRequest, fetchData} from "../Effects";
import Button from "react-bootstrap/Button";
import {storeEncState} from "./Enc";
import Loading from "./Loading";

type LayerQueryPage = {
    lastId: number,
    items: Array<LayerQueryResult>,
}

type LayerQueryResult = {
    id: number,
    lat: number,
    lng: number,
    zoom: number,
    props: Record<string, string | number>,
    chartName: string,
    geomType: string,
}

const liStyle = {
    padding: 4
};

export function LayerLocate() {
    let {layer} = useParams();
    const [result, setPages] = useState(Array<LayerQueryResult>())
    const [next, setNext] = useState(0)
    const [page, setPage] = useState(1)
    let navigate = useNavigate()

    const [loading, setLoading] = useState(true)
    useEffect(() => {
        if (loading && result.length === 0) {
            fetchData(`/v1/feature/layer/${layer}?start_id=${next}`, onComplete)
        }
    }, [layer, loading, result])

    function onComplete(response: LayerQueryPage) {
        setLoading(false)
        setPages(result.concat(response.items))
        setNext(response.lastId)
    }

    function nextPage() {
        setPage(page + 1)
        setLoading(true)
        fetchData(`/v1/feature/layer/${layer}?start_id=${next}`, onComplete)
    }

    return (
        <div className="container Content">
            <h1>
                Chart Locations for layer: {layer} page {page}
            </h1>
            {loading && <Loading/>}
            <ol>
                {result.map((each, i) => {
                    return <li style={liStyle} key={i}>
                        {each.chartName}, {each.lat}, {each.lng}, {each.zoom}, {each.geomType} <Button size="sm"
                                                                                                       variant="outline-success"
                                                                                                       onClick={() => {
                                                                                                           storeEncState({
                                                                                                               lat: each.lat,
                                                                                                               lng: each.lng,
                                                                                                               zoom: each.zoom
                                                                                                           })
                                                                                                           navigate("/enc")
                                                                                                       }}>ENC Zoom
                        to</Button>
                        {
                            Object.keys(each.props).filter(ea => `${each.props[ea]}`.length > 0).map((ea, i) => {
                                return <div key={i}><strong>{ea}</strong>: {each.props[ea]} </div>
                            })
                        }
                    </li>
                })
                }
            </ol>
            {loading && next > 0 &&<Loading/>}
            {!loading && next > 0 && <>
                <Button onClick={() => {
                    nextPage()
                }} size="sm">
                    more
                </Button>
            </>}
        </div>
    )
}