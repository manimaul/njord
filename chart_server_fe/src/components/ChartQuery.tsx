import {useState} from "react";
import {useRequests} from "../Effects";
import {MapGeoJSONFeature} from "maplibre-gl"
import {GeoJSON} from "geojson"
import Accordion from 'react-bootstrap/Accordion';
import {S57Object, S57Attribute, S57ExpectedInput} from "../model/S57Objects"
import Loading from "./Loading";
import SvgCircle from "./SvgCircle";

type Query = {
    feature?: MapGeoJSONFeature;
    eventKey: string
}

type ExpectedInput = Map<String, Array<S57ExpectedInput>>;
// type ObjMap = Map<String, S57Object>;
type AttMap = Map<String, S57Attribute>;

type QueryState = {
    obj?: S57Object;
    att: AttMap;
    input?: ExpectedInput;
    properties?: {
        [name: string]: any;
    };
    colors: Map<String, String>;
}

type ChartPropsProps = {
    qs?: QueryState,
}

function ChartProps(props: ChartPropsProps) {
    let listItems: Map<string, Array<any>> = new Map();
    let displayProps = props.qs?.properties ? Object.keys(props.qs?.properties).filter(key => {
        switch (key) {
            case "SY": //point symbol
            case "AC": //area color
            case "LC": //area color
            case "AP": //area pattern
            case "DEBUG":
            case "PLY":
                return false
        }
        let value = props.qs?.properties?.[key];
        if (typeof value === 'string') {
            let sVal: string = value;
            if (sVal.startsWith("[") && sVal.endsWith("]")) {
                console.log("chart props list item: " + sVal);
                console.log("chart props list item: " + typeof sVal);
                try {
                    let objs = JSON.parse(sVal);
                    listItems.set(key, objs);
                    console.log(`${key}, ${sVal}, ${objs}, ${listItems}`);
                } catch (error) {
                    listItems.set(key, [`${sVal}`]);
                }
                return false;
            }
        }
        return true;
    }) : [];

	function PropProps(att?: S57Attribute , input?: Array<any>) {
		let meanings= input?.map(id => {
			let input = props.qs?.input?.get(`${att?.Code}`)
			return input?.filter(ei => `${ei.ID}` === `${id}`)?.map(ei => {
				return ei.Meaning
			})
		})
        let len = meanings?.length ?? 0;
		if (att && len > 1 ) {
			return (
				<>
                    <strong>({input?.toString()})</strong>
					<ul>
						{meanings?.map(m => <li>{m}</li>)}
					</ul>
				</>
			)
		} else if (input && len === 1) {
			return (
				<>
                    <strong>{input?.toString()} {meanings}</strong>
                </>
			)

		}
		return (
			<></>
		)
	}
    return (
        <div>
            {
                Array.from(listItems.keys()).map((key, i) => {
					let att = props.qs?.att?.get(key);
					return (
						<div key={`${key}_${i}`}>
							<a href={`/control/symbols/${props.qs?.obj?.Acronym}/${key}`} rel="noreferrer"
							  target="_blank">{key}</a> - {att?.Attribute} {PropProps(att, listItems.get(key))}
						</div>);
                })
            }
            {
                displayProps.map((key, i) => {
					let att = props.qs?.att?.get(key);
					let value = props.qs?.properties?.[key];
                    return (
                        <div key={`${key}_${i}`}>
							<a href={`/control/symbols/${props.qs?.obj?.Acronym}/${key}`} rel="noreferrer"
							   target="_blank">{key}</a> - {att?.Attribute} {PropProps(att, [value])}
						</div>
					);
                })
            }
        </div>
    );
}

export default function ChartQuery(props: Query) {
    const [qs, setQs] = useState<QueryState | null>(null);
    let name = props.feature?.sourceLayer;

    useRequests(["/v1/about/s57objects", "/v1/about/s57attributes", "/v1/about/expectedInput", "/v1/about/colors"], responses => {
        if (name) {
            let objMap = new Map<String, S57Object>(Object.keys(responses[0]).map(key => [key, responses[0][key]]));
            let atts = new Map(Object.keys(responses[1]).map(key => [key, responses[1][key]]));
            let colors = new Map<String, String>(Object.keys(responses[3]["DAY"]).map(key => [key,responses[3]["DAY"][key]]))
            let qs: QueryState = {
                obj: objMap.get(name!),
                att: atts,
                input: new Map(Object.keys(responses[2]).map(key => [key, responses[2][key]])),
                properties: props.feature?.properties,
                colors: colors
            }
            setQs(qs);
        }
    })
    let imageSymbol = qs?.properties?.["SY"]
    let areaColor = qs?.properties?.["AC"]
    let lineColor = qs?.properties?.["LC"]
    let linePattern = qs?.properties?.["LP"]
    let areaPattern = qs?.properties?.["AP"]

    if (qs) {
        return (
            <>
                <Accordion.Item eventKey={`${props.eventKey}`}>
                    <Accordion.Header>{qs?.obj?.ObjectClass} - ({name})</Accordion.Header>
                    <Accordion.Body>
                        <p><strong>Geometry: </strong>{props?.feature?.geometry?.type}</p>
                        <LatLng geo={props.feature?.geometry}/>
                        {imageSymbol && <>
                            <p>
                                <strong>Symbol: </strong>SY {imageSymbol} <img src={`/v1/icon/${imageSymbol}.png`} alt={imageSymbol}/>
                            </p>
                        </>}
                        {areaPattern && <>
                            <p>
                                <strong>Area pattern: </strong>AP {areaPattern} <img src={`/v1/icon/${areaPattern}.png`} alt={areaPattern}/>
                            </p>
                        </>}
                        {areaColor && <>
                            <pre>
                                <strong>Area Color: </strong><span>AC {areaColor} </span><SvgCircle color={qs.colors.get(areaColor)} />
                            </pre>
                        </>}
                        {lineColor && <>
                            <pre>
                                <strong>Line Color: </strong><span>LC {lineColor} </span><SvgCircle color={qs.colors.get(lineColor)} />
                            </pre>
                        </>}
                        {linePattern && <>
                            <p>
                                <strong>Line pattern: </strong>LP {linePattern} <img src={`/v1/icon/${linePattern}.png`} alt={linePattern}/>
                            </p>
                        </>}
                        <ChartProps qs={qs}/>
                    </Accordion.Body>
                </Accordion.Item>
            </>
        );
    } else {
        return (<Loading/>);
    }
}

type Geom = {
    geo?: GeoJSON.Geometry
}

function LatLng(props: Geom) {
    if (props.geo?.type === 'Point') {
        let lng = props.geo?.coordinates[0];
        let lat = props.geo?.coordinates[1];
        return (<p><strong>Position: </strong>{lat}, {lng}</p>);
    } else {
        return (<></>);
    }
}

