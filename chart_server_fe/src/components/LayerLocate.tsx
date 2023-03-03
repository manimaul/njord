import {useParams} from "react-router-dom";
import {useNavigate} from "react-router-dom";
import {useState} from "react";
import {useRequest} from "../Effects";
import Button from "react-bootstrap/Button";
import {storeEncState} from "./Enc";

type LayerQueryResult = {
    lat: number,
    lng: number,
    zoom: number,
    props: Record<string, string | number>,
    chartName: string
}

const liStyle = {
    padding: 4
};

export function LayerLocate() {
    let {layer} = useParams();
    const [result, setResult] = useState<LayerQueryResult[] | null>(null)
    useRequest(`/v1/feature?layer=${layer}`, setResult)
    let navigate = useNavigate();

    return (
        <div className="container Content">
            <h1>
                Chart Locations for layer: {layer}
            </h1>
            <ol>
                {result && result.map((each, i) => {
                    return <li style={liStyle} key={i}>
                        {each.chartName}, {each.lat}, {each.lng}, {each.zoom} <Button size="sm"
                                                                                      variant="outline-success"
                                                                                      onClick={() => {
                                                                                          storeEncState({
                                                                                              lat: each.lat,
                                                                                              lng: each.lng,
                                                                                              zoom: each.zoom
                                                                                          })
                                                                                          navigate("/enc")
                                                                                      }}>ENC Zoom to</Button>
                        {
                            Object.keys(each.props).filter(ea => `${each.props[ea]}`.length > 0).map((ea, i) => {
                                return <div key={i}><strong>{ea}</strong>: {each.props[ea]} </div>
                            })
                        }
                    </li>
                })
                }
            </ol>
        </div>
    )
}