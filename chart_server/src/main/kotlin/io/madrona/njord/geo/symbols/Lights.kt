package io.madrona.njord.geo.symbols

/**
 * Attribute type: L 	Used in: 	LIGHTS
 * ID   Meaning	                INT 1       S-4
 * 1    directional function	IP 30.1-3;	475.7;
 * 2    rear/upper light
 * 3    front/lower light
 * 4    leading light           IP 20.1-3;	475.6;
 * 5    aero light              IP 60;	    476.1;
 * 6    air obstruction light   IP 61.1-2	476.2;
 * 7    fog detector light      IP 62;	    477;
 * 8    flood light             IP 63;	    478.2;
 * 9    strip light             IP 64;	    478.5;
 * 10   subsidiary light        IP 42;	    471.8;
 * 11   spotlight
 * 12   front
 * 13   rear
 * 14   lower
 * 15   upper
 * 16   moire effect	        IP 31;	    475.8;
 * 17   emergency
 * 18   bearing light	 	                478.1;
 * 19   horizontally disposed
 * 20   vertically disposed
 * Remarks:
 * Marine light (a light intended primarily for marine navigation) is not included in the above list. All lights are
 * considered to be marine lights unless the attribute 'category of light' indicates otherwise.
 **/
enum class Catlit(val code: Int) {
    DirectionalFunction(1),
    RearUpperLight(2),
    FrontLowerLight(3),
    LeadingLight(4),
    AeroLight(5),
    AirObstructionLight(6),
    FogDetectorLight(7),
    FloodLight(8),
    StripLight(9),
    SubsidiaryLight(10),
    Spotlight(11),
    Front(12),
    Rear(13),
    Lower(14),
    Upper(15),
    MoireEffect(16),
    Emergency(17),
    BearingLight(18),
    HorizontallyDisposed(19),
    VerticallyDisposed(20);

    companion object {
        fun fromProp(prop: S57Prop?): Catlit? {
            return prop?.let {
                it["CATLIT"]?.toString()?.toIntOrNull()
            }?.let { code ->
                values().firstOrNull { it.code == code }
            }
        }
    }
}

fun S57Prop.addLights() {
    val catlit = Catlit.fromProp(this)
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