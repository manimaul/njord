import {useState} from "react";
import {useParams} from "react-router";
import {useRequest} from "./Effects";
import {Dropdown} from "react-bootstrap";


type PathToAProps = {
    path: string;
};
function PathToA(props: PathToAProps) {
    return (
        <a href={props.path}>{props.path}</a>
    )
}

function S57Objects(props: ObjMapProps) {
    return (
        <div className="col">
            <h2>S57 Objects</h2>
            <PathToA path="/v1/about/s57objects"/>
            <br />
            <br />
            <Dropdown >
                {
                    Object.keys(props.objects).map((each, i) => {
                        return <p>{each}</p>
                    })
                }
            </Dropdown>
        </div>
    )
}

type ObjMapProps = {
    objects: Object
};

export default function ChartSymbols() {
    const [objMap, setObjMap] = useState({})
    const [atts, setAtts] = useState({})
    const [exIn, setExIn] = useState({})
    let params = useParams()
    useRequest("/v1/about/s57objects", setObjMap)
    useRequest("/v1/about/s57attributes", setAtts)
    useRequest("/v1/about/expectedInput", setExIn)

    return (
        <div className="container">
            <div className="row">
                <S57Objects objects ={objMap}/>
            </div>
        </div>
    )
}
