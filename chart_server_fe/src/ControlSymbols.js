import {useState} from "react";
import {useParams} from "react-router";


export default function ChartSymbols() {
    const [objMap, setObjMap] = useState({})
    const [atts, setAtts] = useState({})
    const [exIn, setExIn] = useState({})
    let params = useParams()

    return (
        <div>
            foo
        </div>
    )
}
