import React, {useState} from "react";
import {useRequest} from "../Effects";
import {pathToFullUrl} from "../Util";
import {Dropdown} from "react-bootstrap";

function spriteJsonUrl(theme: string) {
  return pathToFullUrl(`/v1/content/sprites/${theme}_simplified@2x.json`)
}

function spritePngUrl(theme: string) {
  return pathToFullUrl(`/v1/content/sprites/${theme}_simplified@2x.png`)
}

function imgStyle(theme: string, value: any): React.CSSProperties {
  return {
    width: `${value.width}px`,
    height: `${value.height}px`,
    background: `url(${spritePngUrl(theme)})`,
    backgroundPosition: `-${value.x}px -${value.y}px`,
    display: 'inline-block',
    borderWidth: '4px'
  };
}

export function Sprites() {
  const options = ['day', 'dusk', 'night'];
  const [dayThemeData, setDayThemeData] = useState<any>({})
  const [duskThemeData, setDuskThemeData] = useState<any>({})
  const [nightThemeData, setNightThemeData] = useState<any>({})
  const [color, setColor] = useState<number>(0)
  useRequest("/v1/content/sprites/day_simplified@2x.json", setDayThemeData)
  useRequest("/v1/content/sprites/dusk_simplified@2x.json", setDuskThemeData)
  useRequest("/v1/content/sprites/night_simplified@2x.json", setNightThemeData)

  function themeData() {
    switch (color) {
      case 0:
        return dayThemeData;
      case 1:
        return duskThemeData;
      case 2:
        return nightThemeData;
      default:
        return dayThemeData;
    } 
  }

  return (
    <div>
      <h2>Chart Symbol Sprites</h2>
      <Dropdown>
        <Dropdown.Toggle variant="success" id="dropdown-basic">
          {options[color]}
        </Dropdown.Toggle>
        <Dropdown.Menu>
          {
            options.map((each, i) =>
              <Dropdown.Item key={`${i}`} onClick={() => setColor(i)}>{each}</Dropdown.Item>
            )
          }
        </Dropdown.Menu>

      </Dropdown>
      <br />
      <div>
        Sprite sheet: <a href={spritePngUrl(options[color])}>{spritePngUrl(options[color])}</a>
      </div>
      <div>
        Sprite json: <a href={spriteJsonUrl(options[color])}>{spriteJsonUrl(options[color])}</a>
      </div>
      <br />
      <div className="col">
        <div className="row">
          {
            Object.keys(themeData()).map(key => {
              return (
                <div key={key + `${options[color]}`} className="col-sm">
                  {key}
                  <br />
                  <div style={imgStyle(options[color], themeData()[key])}/>
                </div>
              )
            })}
        </div>
      </div>
    </div>
  )
}
