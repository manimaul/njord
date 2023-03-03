import {useEffect, useState} from "react";
import {useRequests} from "../Effects";
import {Dropdown} from "react-bootstrap";
import Form from "react-bootstrap/Form";
import {useNavigate} from "react-router";
import {Link} from "react-router-dom";
import {S57Object, S57Attribute, S57ExpectedInput} from "../model/S57Objects"
import Button from "react-bootstrap/Button";


type PathToAProps = {
    path: string;
};

function PathToA(props: PathToAProps) {
    return (
        <a href={props.path}>{props.path}</a>
    )
}

type ObjMapProps = {
    objects: Map<String, S57Object>,
    selected: string,
    attribute?: string
};

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
			<Link to={`/layer/${object?.Acronym}`}><Button>Locate on Chart</Button></Link>
			<br />
			<br />
			<p><strong>Geometry Primitives: </strong>{geometryPrimitives()}</p>
			<p><strong>Object: </strong>{object?.ObjectClass}</p>
			<p><strong>Acronym: </strong>{object?.Acronym}</p>
			<p><strong>Code: </strong>{object?.Code}</p>
			<AttributeSet 
				selectedObject={props.selected} 
				name="Attribute_A" 
				desc="(Attributes in this subset define the individual characteristics of the object.)" 
				attributes={object?.Attribute_A} />
			<AttributeSet 
				selectedObject={props.selected} 
				name="Attribute_B" 
				desc="(Attributes in this subset provide information relevant to the use of the data, e.g. for presentation or for an information system.)" 
				attributes={object?.Attribute_B} />
			<AttributeSet 
				selectedObject={props.selected} 
				name="Attribute_C" 
				desc="(Attributes in this subset provide administrative information about the object and data describing it.)" 
				attributes={object?.Attribute_C} />
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
			{props.attributes ? props.attributes.map((each, i) => 
				<span key={i}>
					<Link to={`/control/symbols/${props.selectedObject}/${each}`}>{each}</Link> 
				{" "}</span>) : <span>Attributes missing</span>}
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
            { (props.attribute) ? showAttribute(props.attribute, props.input) : <span>Attribute not selected</span> }
            <br/>
        </div>
    )
}


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
	useRequests(["/v1/about/s57objects", "/v1/about/s57attributes", "/v1/about/expectedInput"], responses => {
		setObjMap(new Map(Object.keys(responses[0]).map(key => [key, responses[0][key]])));
        setAtts(new Map(Object.keys(responses[1]).map(key => [key, responses[1][key]])));
        setExIn(new Map(Object.keys(responses[2]).map(key => [key, responses[2][key]])));
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


