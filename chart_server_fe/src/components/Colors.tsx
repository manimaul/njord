import React, {useState} from "react";
import {useRequest, useTypeRequest} from "../Effects";
import {pathToFullUrl} from "../Util";
import {Dropdown} from "react-bootstrap";
import Form from "react-bootstrap/Form";
import {S57Attribute} from "../model/S57Objects";


type ColorValue = Record<string, string>;
type ColorMap = Record<string, ColorValue>;

type ColorProp = {
    acronym: string,
    hex: string,
}

function Color(props: ColorProp) {
    return (
       <div>
           {props.acronym} {props.hex}
           <div style={{width:25, height:25, background:props.hex}}></div>
       </div>
    )
}

function selected(response: ColorMap | null, index: number) : string {
    if (response != null) {
        let options = Object.keys(response)
        if (index < options.length) {
            return options[index];
        }
    }
    return "";
}

function selectedColors(response: ColorMap | null, index: number) : ColorProp[] {
    if (response != null) {
        let key = selected(response, index);
        let value: ColorValue | undefined = response[key] //response.get(key)
        if (value != undefined) {
            return [ ...Object.keys(value).map((acronym) => {
                let p: ColorProp = {
                    acronym:acronym,
                    hex: `${value?.[acronym]}`
                };
                return p
            })]
        }
    }
    return [];
}
export function Colors() {
    const [colorData, setColorData] = useState<ColorMap | null>(null)
    const [color, setColor] = useState<number>(0)
    useRequest("/v1/about/colors", setColorData)

    return (
        <div>
            <h2>Chart Symbol Sprites</h2>
            <Dropdown>
                <Dropdown.Toggle variant="success" id="dropdown-basic">
                    {selected(colorData, color)}
                </Dropdown.Toggle>
                <Dropdown.Menu>
                    {
                        colorData != null && Object.keys(colorData).map((each, i) =>
                            <Dropdown.Item key={`${i}`} onClick={() => setColor(i)}>{each}</Dropdown.Item>
                        )
                    }
                </Dropdown.Menu>

            </Dropdown>
            <br />
            <div className="col">
                <div className="row">
                    {
                        colorData != null && selectedColors(colorData, color).map((each, i) =>
                            <div className="col">
                                {each.acronym} {each.hex}
                                <div style={{width:25, height:25, background:each.hex}}></div>
                                <br />
                            </div>
                        )
                    }
                </div>
            </div>

        </div>
    )
}
