import {useEffect, useState} from "react";
import {useRequest} from "../Effects";
import {Dropdown, OverlayTrigger, Tooltip} from "react-bootstrap";
import Form from "react-bootstrap/Form";
import {useNavigate} from "react-router";
import {Link} from "react-router-dom";


type PathToAProps = {
    path: string;
};

function PathToA(props: PathToAProps) {
    return (
        <a href={props.path}>{props.path}</a>
    )
}

function S57Objects(props: ObjMapProps) {
    const [filter, setFilter] = useState("")
    const [options, setOptions] = useState<Array<String>>([])
    useEffect(() => {
        setOptions(Array.from(props.objects.keys()).filter(each => filter === "" || each.toLowerCase().includes(filter.toLowerCase())))
    }, [props.objects, filter])
    const nav = useNavigate()

	function setSelected(selected: string) {
		nav(`/control/symbols/${selected}`);
	}

	let object = props.objects.get(props.selected)

	function geometryPrimitives(): string {
		if (object) {
			return object.Primitives.reduce((acc, value) => `${acc}, ${value}`);
		}
		return ""
	}

    return (
        <div className="col">
            <h2>S57 Object</h2>
            <div><PathToA path="/v1/about/s57objects"/></div>
            <br/>
            <Dropdown>
                <Dropdown.Toggle variant="success" id="dropdown-basic">
                    {props.selected}
                </Dropdown.Toggle>
                <Dropdown.Menu>
                    <Form.Control
                        autoFocus
                        className="mx-3 my-2 w-auto"
                        placeholder="Type to filter..."
                        onChange={(e) => setFilter(e.target.value)}
                        value={filter}
                    />
                    {
                        options.map((each, i) =>
                            <Dropdown.Item key={`${i}`} onClick={() => setSelected(`${each}`)}>{each}</Dropdown.Item>
                        )
                    }
                </Dropdown.Menu>

            </Dropdown>
            <br />
			<p><strong>Geometry Primitives: </strong>{geometryPrimitives()}</p>
			<p><strong>Object: </strong>{object?.ObjectClass}</p>
			<p><strong>Acronym: </strong>{object?.Acronym}</p>
			<p><strong>Code: </strong>{object?.Code}</p>
			<AttributeSet selectedObject={props.selected} name="Attribute_A" desc="(Attributes in this subset define the individual characteristics of the object.)" attributes={object?.Attribute_A} />
			<AttributeSet selectedObject={props.selected} name="Attribute_B" desc="(Attributes in this subset provide information relevant to the use of the data, e.g. for presentation or for an information system.)" attributes={object?.Attribute_B} />
			<AttributeSet selectedObject={props.selected} name="Attribute_C" desc="(Attributes in this subset provide administrative information about the object and data describing it.)" attributes={object?.Attribute_C} />
        </div>
    )
}



type AttSetProps = {
	selectedObject: string,
	name: string,
	desc: string,
	attributes?: Array<string>,
}

function AttributeSet(props: AttSetProps) {
	return(
		<p>
			<strong>{props.name}</strong>
			<br />
			{props.desc}
			<br />
			{props.attributes ? props.attributes.map((each, i) => <span key={i}><Link to={`/control/symbols/${props.selectedObject}/${each}`}>{each}</Link> </span>) : <span>Attributes missing</span>}
		</p>
	)
}

type AttProps = {
    attribute?: S57Attribute,
    input?: Map<String, Array<S57ExpectedInput>>
}

function showInput(inputs: Array<S57ExpectedInput>) {
	inputs.forEach(each => console.log(`fount expected input ${each.ID}`));
	if (inputs.length === 0) {
		return (<p>No expected inputs</p>)
	}
	return (
		<table>
			<tbody>
			<tr><th>ID</th><th>Meaning</th></tr>
			{
				inputs.map(
					(each, i) => {
						return (
							<tr key={i}><td>{each.ID}</td><td>{each.Meaning}</td></tr>
						)
					}
				)
			}
			</tbody>
		</table>
	)
}

function showAttribute(
	att: S57Attribute,
	input?: Map<String, Array<S57ExpectedInput>>
) {
	let selectedInput: Array<S57ExpectedInput> | undefined = input?.get(`${att.Code}`);
	return (
		<div>
			<br />
			<p><strong>Attribute: </strong>{att.Attribute}</p>	
			<p><strong>Acronym: </strong>{att.Acronym}</p>	
			<p><strong>Code: </strong>{att.Code}</p>	
			{ (selectedInput) ? showInput(selectedInput) : "" }
			<br />
			<p><strong>Attribute type: </strong>{att.Attributetype}</p>	
			<ul>
				<li> Attribute type: one-character code for the attribute type - there are six possible types:</li>
				<li> Enumerated ("E") - the expected input is a number selected from a list of predefined attribute values; exactly one value must be chosen.</li>
				<li> List ("L") - the expected input is a list of one or more numbers selected from a list of pre-defined attribute values.</li>
				<li> Float ("F") - the expected input is a floating point numeric value with defined range, resolution, units and format.</li>
				<li> Integer ("I") - the expected input is an integer numeric value with defined range, units and format.</li>
				<li> Coded String ("A") - the expected input is a string of ASCII characters in a predefined format; the information is encoded according to defined coding systems.</li>
				<li> Free Text ("S") - the expected input is a free-format alphanumeric string; it may be a file name which points to a text or graphic file.</li>
			</ul>
			<p><strong>Attribute class: </strong>{att.Class}</p>	
		</div>
	)
}

function S57Attributes(props: AttProps) {
    return (
        <div className="col">
            <h2>S57 Attribute</h2>
            <div><PathToA path="/v1/about/s57attributes"/></div>
            <div><PathToA path="/v1/about/expectedInput"/></div>
            { (props.attribute) ? showAttribute(props.attribute, props.input) : <span>Attribute not selected</span> }
            <br/>
        </div>
    )
}

type ObjMapProps = {
    objects: Map<String, S57Object>,
    selected: string,
    attribute?: string
};

type ChartSymbolProps = {
	object?: string,
	attribute?: string,
}

/**
 * Chart Symbols Tab
 */
export default function ChartSymbols(props: ChartSymbolProps) {
    const [objMap, setObjMap] = useState<Map<String, S57Object>>(new Map())
    const [atts, setAtts] = useState<Map<String, S57Attribute>>(new Map())
    const [exIn, setExIn] = useState<Map<String, Array<S57ExpectedInput>>>(new Map())
    useRequest("/v1/about/s57objects", response => {
        setObjMap(new Map(Object.keys(response).map(key => [key, response[key]])))
    })
    useRequest("/v1/about/s57attributes", response => {
        setAtts(new Map(Object.keys(response).map(key => [key, response[key]])))
    })
    useRequest("/v1/about/expectedInput", response => {
        setExIn(new Map(Object.keys(response).map(key => [key, response[key]])))
    })

	function getAttribute() :S57Attribute | undefined {
		if (props.attribute) {
			return atts.get(props.attribute);
		} else {
			return undefined;
		}
	}

	function getObject() :string {
		if (props.object) {
			return props.object
		} else {
			return objMap.keys().next().value
		}
	}

    return (
        <div>
            <div className="row">
                <S57Objects objects={objMap} selected={getObject()} attribute={props.attribute}/>
                <S57Attributes attribute={getAttribute()} input={exIn}/>
            </div>
        </div>
    )
}

export type S57Attribute = {
    /**
     * Unique code for the attribute.
     */
    Code: number,

    /**
     * Human-readable description of the attribute.
     */
    Attribute: string,

    /**
     * Six character acronym key for the attribute.
     */
    Acronym: string,

    /**
     * Attribute type: one-character code for the attribute type - there are six possible types:
     * Enumerated ("E") - the expected input is a number selected from a list of predefined attribute values; exactly one value must be chosen.
     * List ("L") - the expected input is a list of one or more numbers selected from a list of pre-defined attribute values.
     * Float ("F") - the expected input is a floating point numeric value with defined range, resolution, units and format.
     * Integer ("I") - the expected input is an integer numeric value with defined range, units and format.
     * Coded String ("A") - the expected input is a string of ASCII characters in a predefined format; the information is encoded according to defined coding systems.
     * Free Text ("S") - the expected input is a free-format alphanumeric string; it may be a file name which points to a text or graphic file.
     */
    Attributetype: string,

    /**
     * todo: (what do these mean?) F= $= N= S= ?=
     */
    Class: string,
}

export type S57Object = {
    /**
     * Unique code for the object.
     */
    Code: number,

    /**
     * Human-readable description of the object.
     */
    ObjectClass: string,

    /**
     * Six character acronym key for the object.
     */
    Acronym: string

    /**
     * Attributes in this subset define the individual characteristics of the object.
     */
    Attribute_A: Array<string>,

    /**
     * Attributes in this subset provide information relevant to the use of the data, e.g. for presentation or for an
     * information system.
     */
    Attribute_B: Array<string>,

    /**
     * Attributes in this subset provide administrative information about the object and data describing it.
     */
    Attribute_C: Array<string>,


    /**
     * todo: (what do these mean?) G= M= C= $= <empty>=
     */
    Class: string,

    /**
     * The geometric primitives allowed for the object are P=point L=line A=area N=none
     */
    Primitives: Array<string>
}

export type S57ExpectedInput = {
    /**
     * The corresponding [S57Attribute.code]
     */
    Code: number,

    /**
     * Value in the [S57Object]'s [S57Attribute]
     * eg A BOYSPP feature with an attribute: CATSPM: ["27"]
     * CATSPM has id 66 so the [S57ExpectedInput] with Code: 66 and ID: 27 has the [S57ExpectedInput.meaning]: "general warning mark"
     },
     */
    ID: number,

    /**
     * Human readable description
     */
    Meaning: string,
}
