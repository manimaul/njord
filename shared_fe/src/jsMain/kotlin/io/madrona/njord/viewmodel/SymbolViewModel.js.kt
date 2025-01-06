package io.madrona.njord.viewmodel

import io.madrona.njord.model.S57Attribute
import io.madrona.njord.model.S57ExpectedInput
import io.madrona.njord.model.S57Object
import io.madrona.njord.routing.Route
import io.madrona.njord.viewmodel.utils.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SymbolState(
    val selectedObj: Async<S57Object> = Uninitialized,
    val selectedAtt: Async<S57Attribute> = Uninitialized,
)

data class SelectedAttribute(
    val att: S57Attribute,
    val inputs: List<S57ExpectedInput>
)

val symbolViewModel = SymbolViewModel()

@OptIn(ExperimentalCoroutinesApi::class)
class SymbolViewModel : BaseViewModel<SymbolState>(SymbolState()) {
    init {
        launch {
            val att = chartObjectsViewModel.flow.map { it.attributes }.filter {
                it is Complete
            }.mapNotNull { it.value }

            val obj = chartObjectsViewModel.flow.map { it.s57Objects }.filter {
                it is Complete
            }.mapNotNull { it.value }

            routeViewModel.flow.filter {
                it.current.route == Route.ControlPanel && it.current.path.startsWith("/control/symbols")
            }.map { it.current.pathSegments }.flatMapMerge { pathSegments ->
                obj.map { it[pathSegments.getOrNull(2)] }.flatMapMerge { selectedObj ->
                    att.map { selectedObj to it[pathSegments.getOrNull(3)] }
                }
            }.collect {
                setState {
                    copy(
                        selectedObj = it.first?.let { Complete(it) } ?: Uninitialized,
                        selectedAtt = it.second?.let { Complete(it) } ?: Uninitialized,
                    )
                }
            }
        }
    }

    override fun reload() {
        chartObjectsViewModel.reload()
    }

    val expectedInputFlow: Flow<Async<SelectedAttribute>> = flow.map { it.selectedAtt }
        .flatMapMerge { asyncAtt ->
            chartObjectsViewModel.flow.map { it.expectedInputs }
                .map { asyncEi ->
                    asyncEi.flatMap { expectedInput ->
                        asyncAtt.map { att ->
                            SelectedAttribute(
                                att,
                                expectedInput["${att.code}"] ?: emptyList()
                            )
                        }
                    }
                }
        }

    fun selectObj(name: String) {
        routeViewModel.pushRoute("/control/symbols/$name")
    }

    fun selectAtt(name: String) {
        withState { state ->
            routeViewModel.pushRoute("/control/symbols/${state.selectedObj.value?.acronym}/$name")
        }
    }
}