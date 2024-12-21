package io.madrona.njord.viewmodel

import io.madrona.njord.model.*
import io.madrona.njord.network.Network
import io.madrona.njord.viewmodel.utils.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ColorSelection(
    val mode: ThemeMode,
    val themeColors: Async<Map<Color, String>>
)

data class ChartObjectsState(
    val s57Objects: Async<Map<String, S57Object>> = Uninitialized,
    val attributes: Async<Map<String, S57Attribute>> = Uninitialized,
    val expectedInputs: Async<Map<String, List<S57ExpectedInput>>> = Uninitialized,
    val colors: Async<Map<ThemeMode, Map<Color, String>>> = Uninitialized,
)

val chartObjectsViewModel = ChartObjectsViewModel()

class ChartObjectsViewModel : BaseViewModel<ChartObjectsState>(ChartObjectsState()) {
    init {
        reload()
        launch {
            chartViewModel.flow.map { it.theme.mode() }.collect { mode ->
                withStateAsync { state ->
                    internalColorStateFlow.setState {
                        copy(
                            mode = mode,
                            themeColors = state.colors.mapNotNull { it[mode] }
                        )
                    }
                }
            }
        }
    }

    private val internalColorStateFlow = MutableStateFlow(
        ColorSelection(mode = currentThemeMode(), Uninitialized)
    )

    val colorSelectionFlow: StateFlow<ColorSelection>
        get() = internalColorStateFlow

    override fun reload() {
        setState {
            copy(
                s57Objects = Loading(),
                attributes = Loading(),
                expectedInputs = Loading(),
            )
        }
        setState {
            copy(
                s57Objects = Network.getS57Objects().toAsync(),
                attributes = Network.getAttributes().toAsync(),
                expectedInputs = Network.getExpectedInputs().toAsync(),
                colors = Network.getColors().toAsync(),
            )
        }
    }
}