package io.madrona.njord.geo.symbols


fun S57Prop.addLights() {
    val color = Color.fromProp(this)
    val sy = when(color) {
        listOf(Color.Red) -> "LIGHTS11"
        listOf(Color.Green) -> "LIGHTS12"
        listOf(Color.White),
        listOf(Color.Yellow) -> "LIGHTS13"
        else -> "LIGHTDEF"
    }
    this["SY"] = sy
}