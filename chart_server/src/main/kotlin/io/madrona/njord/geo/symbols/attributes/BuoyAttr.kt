package io.madrona.njord.geo.symbols.attributes

import io.madrona.njord.geo.symbols.S57Prop

/**
 *
 * Attribute type: E	Used in: BCNLAT, BOYLAT
 *
 * Expected input:
 * ID	Meaning	INT 1	S-4
 * 1	port-hand lateral mark	IQ 91-92; IQ 130.1 Region A; IQ 130.1 Region B	456.1;
 * 2	starboard-hand lateral mark	IQ 91-92; IQ 130.1 Region A; IQ 130.1 Region B	456.1;
 * 3	preferred channel to starboard lateral mark	IQ 130.1 Region A; IQ 130.1 Region B
 * 4	preferred channel to port lateral mark	IQ 130.1 Region A; IQ 130.1 Region B
 * Remarks:
 * There are two international buoyage regions, A and B, between which lateral marks differ. The buoyage region is encoded
 * using the separate attribute MARSYS. When top-marks, retro reflectors and/or lights are fitted to these marks, they are
 * encoded as separate objects.
 */
enum class Catlam(val code: Int) {
    PortHandLateralMark(1),
    StarboardHandLateralMark(2),
    PreferredChannelToStarboardLateralMark(3),
    PreferredChannelToPortLateralMark(4);

    companion object {
        fun fromProp(prop: S57Prop?): Catlam? {
            return prop?.let {
                it["CATLAM"]?.toString()?.toIntOrNull()
            }?.let { code ->
                values().firstOrNull { it.code == code }
            }
        }
    }
}

/**
 * BCNCAR
 *
 * Attribute type: E	Used in: BCNCAR, BCNISD, BCNLAT, BCNSAW, BCNSPP
 *
 * Expected input:
 * ID	Meaning	INT 1	S-4
 * 1	stake, pole, perch, post	IQ 90;	456.1;
 * 2	withy	IQ 92;	456.1;
 * 3	beacon tower	IQ 110;	456.4;
 * 4	lattice beacon	IQ 111;	456.4;
 * 5	pile beacon
 * 6	cairn	IQ 100;	456.2;
 * 7	buoyant beacon	IP 5	459.1-2
 * Remarks:
 * The beacon shape describes the characteristic geometric form of the beacon.
 */
enum class Bcnship(val code: Int) {

}

/**
 * BOYSHP
 *
 * Attribute type: E	Used in: BOYCAR, BOYINB, BOYISD, BOYLAT, BOYSAW, BOYSPP, MORFAC
 *
 * ID	Meaning	INT 1	S-4
 *  1	conical (nun, ogival)	IQ 20;	462.2
 *  2	can (cylindrical)	IQ 21;	462.3
 *  3	spherical	IQ 22;	462.4
 *  4	pillar	IQ 23;	462.5
 *  5	spar (spindle)	IQ 24;	462.6
 *  6	barrel (tun)	IQ 25;	462.7
 *  7	super-buoy	IQ 26;	462.9
 *  8	ice buoy
 */
enum class Boyshp(val code: Int) {
    Conical(1),
    Can(2),
    Spherical(3),
    Pillar(4),
    Spar(5),
    Barrel(6),
    SuperBuoy(7),
    IceBuoy(8);

    companion object {
        fun fromProp(prop: S57Prop?): Boyshp? {
            return prop?.let {
                it["BOYSHP"]?.toString()?.toIntOrNull()
            }?.let { code ->
                values().firstOrNull { it.code == code }
            }
        }
    }
}