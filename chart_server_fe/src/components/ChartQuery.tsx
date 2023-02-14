import React, {useState} from "react";
import {useRequest} from "../Effects";
import {MapGeoJSONFeature} from "maplibre-gl"
import Button from 'react-bootstrap/Button';
import Accordion from 'react-bootstrap/Accordion';
import {S57ExpectedInput, S57Object, S57Attribute} from './ControlSymbols';
import Loading from "./Loading";

type Query = {
	feature?: MapGeoJSONFeature;
	eventKey: string
}

export default function ChartQuery(props: Query) {
    const [obj, setObj] = useState<S57Object | null>(null);
    const [att, setAtt] = useState<S57Attribute | null>(null);
    const [ex, setEx] = useState<Array<S57ExpectedInput>>(new Array());

    const [atts, setAtts] = useState<Map<String, S57Attribute>>(new Map());
    const [exIn, setExIn] = useState<Map<String, Array<S57ExpectedInput>>>(new Map());
	let name = props.feature?.sourceLayer;

	useRequest("/v1/about/s57objects", response => {
		let r = new Map<string, S57Object>(Object.keys(response).map(key => [key, response[key]]));
		if (name) {
			let o = r.get(name!);
			if (o) setObj(o);
		}
	})
	useRequest("/v1/about/s57attributes", response => {
		setAtts(new Map(Object.keys(response).map(key => [key, response[key]])))
	})
	useRequest("/v1/about/expectedInput", response => {
		setExIn(new Map(Object.keys(response).map(key => [key, response[key]])))
	})

	if (obj) {
		return(
			<>
				<Accordion.Item eventKey={`${props.eventKey}`}>
					<Accordion.Header>{obj.ObjectClass} - ({name})</Accordion.Header>
					<Accordion.Body>
						<p><strong>Geometry: </strong>{props?.feature?.geometry?.type}</p>
						<LatLng geo={props.feature?.geometry} />
						<a href={`/control/symbols/${name}`} rel="noreferrer" target="_blank">{`/control/symbols/${name}`}</a>
					</Accordion.Body>
				</Accordion.Item>
			</>
		);
	} else {
		return(<Loading />);
	}
}

type Geom = {
	geo?: GeoJSON.Geometry
}

function LatLng(props: Geom) {
	if (props.geo?.type === 'Point') {
		let lng = props.geo?.coordinates[0];
		let lat = props.geo?.coordinates[1];
		return(<p><strong>Position: </strong>{lat}, {lng}</p>);
	} else {
		return(<></>);
	}
}

