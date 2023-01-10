import {useEffect, useState} from "react";
import {useParams} from "react-router";
import {useRequest} from "../Effects";
import {Dropdown} from "react-bootstrap";
import Form from "react-bootstrap/Form";


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
    const [selected, setSelected] = useState("")
    const [options, setOptions] = useState<Array<String>>([])
    useEffect(() => setSelected(props.selected), [props.selected])
    useEffect(() => {
        setOptions(Array.from(props.objects.keys()).filter(each => filter === "" || each.toLowerCase().includes(filter.toLowerCase())))
    }, [props.objects, filter])

    return (
        <div className="col">
            <h2>S57 Objects</h2>
            <PathToA path="/v1/about/s57objects"/>
            <br/>
            <br/>
            <Dropdown>
                <Dropdown.Toggle variant="success" id="dropdown-basic">
                    {selected}
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
        </div>
    )
}

type  AttProps = {
    slectedObject: string,
    name: string,
    desc: string,
    attributes: Array<string>
}

function S57Attributes(props: AttProps) {
    return (
        <div className="col">

        </div>
    )
}

type ObjMapProps = {
    objects: Map<String, S57Object>,
    selected: string
};

/**
 * Chart Symbols Tab
 */
export default function ChartSymbols() {
    const [objMap, setObjMap] = useState<Map<String, S57Object>>(new Map())
    const [atts, setAtts] = useState<Map<String, S57Attribute>>(new Map())
    const [exIn, setExIn] = useState<Map<String, S57ExpectedInput>>(new Map())
    let params = useParams()
    useRequest("/v1/about/s57objects", response => {
        setObjMap(new Map(Object.keys(response).map(key => [key, response[key]])))
    })
    useRequest("/v1/about/s57attributes", response => {
        setAtts(new Map(Object.keys(response).map(key => [key, response[key]])))
    })
    useRequest("/v1/about/expectedInput", response => {
        setExIn(new Map(Object.keys(response).map(key => [key, response[key]])))
    })

    return (
        <div>
            <div className="row">
                <S57Objects objects={objMap} selected={objMap.keys().next().value}/>
                {/*<S57Attributes />*/}
            </div>
        </div>
    )
}

type S57Attribute = {
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

type S57ExpectedInput = {
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
