import React, {useState} from "react";
import {useRequest} from "../Effects";
import {pathToFullUrl} from "../Util";

const spriteJsonUrl = pathToFullUrl("/v1/content/sprites/simplified@2x.json")
const spritePngUrl = pathToFullUrl("/v1/content/sprites/simplified@2x.png")

function imgStyle(value: any): React.CSSProperties {
    return {
        width: `${value.width}px`,
        height: `${value.height}px`,
        background: `url(${spritePngUrl})`,
        backgroundPosition: `-${value.x}px -${value.y}px`,
        display: 'inline-block',
        borderWidth: '4px'
    };
}
export function Sprites() {
    const [themeData, setThemeData] = useState<any>({})
    useRequest("/v1/content/sprites/simplified@2x.json", setThemeData)
    return (
        <div>
            <h2>Chart Symbol Sprites</h2>
            <br />
            <div>
                Sprite sheet: <a href={spritePngUrl}>{spritePngUrl}</a>
            </div>
            <div>
                Sprite json: <a href={spriteJsonUrl}>{spriteJsonUrl}</a>
            </div>
            <br />
            <div className="col">
                <div className="row">
                    {Object.keys(themeData).map(key => {
                        return (
                            <div key={key} className="col-sm">
                                {key}
                                <br />
                                <div style={imgStyle(themeData[key])}/>
                            </div>
                        )
                    })}
                </div>
            </div>

        </div>
    )
}