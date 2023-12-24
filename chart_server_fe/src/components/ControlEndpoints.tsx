import {pathToFullUrl} from "../Util";
import React from "react";


const endPoints = [
    "/v1/about/version",
    "/v1/about/s57objects",
    "/v1/about/s57attributes",
    "/v1/about/colors",
    "/v1/tile_json",
    "/v1/style/meters",
    "/v1/chart?id=1",
    "/v1/chart_catalog",
    "/v1/geojson?chart_id=17&layer_name=BOYSPP",
    "/v1/tile/0/0/0",
    "/v1/icon/BCNCAR01.png",
    "/v1/content/fonts/Roboto Bold/0-255.pbf",
    "/v1/content/sprites/day_simplified.png",
    "/v1/content/sprites/day_simplified.json",
    "/v1/content/sprites/day_simplified@2x.png",
    "/v1/content/sprites/day_simplified@2x.json",
    "/v1/content/sprites/dusk_simplified.png",
    "/v1/content/sprites/dusk_simplified.json",
    "/v1/content/sprites/dusk_simplified@2x.png",
    "/v1/content/sprites/dusk_simplified@2x.json",
    "/v1/content/sprites/night_simplified.png",
    "/v1/content/sprites/night_simplified.json",
    "/v1/content/sprites/night_simplified@2x.png",
    "/v1/content/sprites/night_simplified@2x.json",
]
export function ControlEndpoints() {
    return (
        <div>
            <h2>GET Endpoints</h2>
            <ol>
                {endPoints.map((each, i) => {
                    return (<li key={i}><a href={pathToFullUrl(each)}>{each}</a></li>)
                })}
            </ol>
        </div>
    )
}