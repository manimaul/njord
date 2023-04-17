import {useEffect, useState} from "react";
import {Admin, useAdmin} from "../Admin";

export type ChartItem = {
    id: number;
    name: string;
};

export type ChartCatalog = {
    totalChartCount: number
    nextId?: number
    page: Array<ChartItem>
}

export type ControlChartsState = {
    totalChartCount: number | null
    filter: string
    deleteProgress: number
    admin: Admin | null
    loadingMore: boolean
    charts: Array<ChartItem>
}

export default function useControlChartsViewModel(): [ControlChartsState, (filter: string) => void, () => void, () => void] {
    const [admin] = useAdmin()
    const [init, setInit] = useState(false)
    const [catalogs, setCatalogs] = useState<Array<ChartCatalog>>([])
    const [state, setState] = useState<ControlChartsState>(defaultState(-1))

    useEffect(() => {
       if (!init) {
           setInit(true)
           loadMore(0)
       }
    }, [init])

    useEffect(() => {
        setState(old => { return {...old, charts: filteredCharts()}})
    }, [catalogs])

    useEffect(() => {
        setState(old => { return {...old, admin: admin}})
    }, [admin])

    useEffect(() => {
        setState(old => { return {...old, charts: filteredCharts()}})
    }, [state.filter])

    function defaultState(deleteProgress: number): ControlChartsState {
        return {
            totalChartCount: null,
            filter: "",
            deleteProgress: deleteProgress,
            admin: admin,
            loadingMore: true,
            charts: [],
        }
    }
    async function loadMore(id: number) {
        console.log("loading more: " + id)
        let response = await fetch(`/v1/chart_catalog?id=${id}`)
        let catalog: ChartCatalog = await response.json()
        setCatalogs(old => [...old, catalog])
        if (id === 0) {
            setState(old => { return {...old, totalChartCount:  catalog.totalChartCount}})
        }
        if (catalog.nextId) {
            loadMore(catalog.nextId)
        } else {
            setState(old => { return {...old, loadingMore: false}})
        }
    }

    function filteredCharts(): Array<ChartItem> {
        let pages = catalogs.map(each => each.page)
            .reduce((acc, each) => acc.concat(each), [])

        return pages.filter(each => {
            if (state.filter.length > 0) {
                return each.name.includes(state.filter)
            }
            return true
        })
    }

    async function deleteCharts() {
        let ids = state.charts.map(each => each.id)
        setState(old => { return {...old, deleteProgress: 0}})
        let increment = 1 / ids.length
        await Promise.all(ids.map(id => {
            return fetch(
                `/v1/chart?id=${id}&signature=${admin?.signatureEncoded}`,
                {
                    method: "DELETE"
                }
            ).then(response => {
                console.log(`delete chart ${id} = ${response.statusText}`)
                setState(old => { return {...old, deleteProgress: (old.deleteProgress ?? 0) + increment}})
            })
        }))
        reload()
    }

    function setFilter(filter: string) {
        setState({...state, filter: filter})
    }

    function reload() {
        if (!state.loadingMore) {
            setCatalogs([])
            setState(defaultState(-1))
            loadMore(0)
        }
    }

    return [state, setFilter, deleteCharts, reload]
}