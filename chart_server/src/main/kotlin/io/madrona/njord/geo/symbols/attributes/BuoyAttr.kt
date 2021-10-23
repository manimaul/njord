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

/**
 * BCNSHP
 *
 * Attribute type: E	Used in: BCNCAR, BCNISD, BCNLAT, BCNSAW, BCNSPP
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
enum class Bcnshp(val code: Int) {
    StakePolePerchPost(1),
    Withy(2),
    Tower(3),
    Lattice(4),
    Pile(5),
    Cairn(6),
    Buoyant(7);

    companion object {
        fun fromProp(prop: S57Prop?): Bcnshp? {
            return prop?.let {
                it["BCNSHP"]?.toString()?.toIntOrNull()
            }?.let { code ->
                values().firstOrNull { it.code == code }
            }
        }
    }
}


/**
 * CATSPM
 *
 * Attribute type: L	Used in: BCNSPP, BOYSPP, DAYMAR, LNDMRK
 *
 * Expected input:
 * ID	Meaning	INT 1	S-4
 * 1	firing danger area mark	IQ 50,125	441.2;
 * 2	target mark	IQ 51;
 * 3	marker ship mark	IQ 52;
 * 4	degaussing range mark	IQ 54;	448.3;
 * 5	barge mark	IQ 53;
 * 6	cable mark	IQ 55, 123;	443.6; 458;
 * 7	spoil ground mark	IQ 56;	446.3;
 * 8	outfall mark	IQ 57;	444.4;
 * 9	ODAS (Ocean-Data-Acquisition-System)	IQ 58;	462.9;
 * 10	recording mark	IQ 59;
 * 11	seaplane anchorage mark	IQ 60;
 * 12	recreation zone mark	IQ 62;
 * 13	private mark	IQ 70;
 * 14	mooring mark	 	431.5;
 * 15	LANBY (Large Automatic Navigational Buoy)	IQ 26;	474.4-5;
 * 16	leading mark	IQ 120;	458;
 * 17	measured distance mark	IQ 122;	458;
 * 18	notice mark	IQ 126;	456.8;
 * 19	TSS mark (Traffic Separation Scheme)	IQ 61;
 * 20	anchoring prohibited mark
 * 21	berthing prohibited mark
 * 22	overtaking prohibited mark
 * 23	two-way traffic prohibited mark
 * 24	'reduced wake' mark
 * 25	speed limit mark	 	456.2;
 * 26	stop mark
 * 27	general warning mark
 * 28	'sound ship's siren' mark
 * 29	restricted vertical clearance mark
 * 30	maximum vessel's draught mark
 * 31	restricted horizontal clearance mark
 * 32	strong current warning mark
 * 33	berthing permitted mark
 * 34	overhead power cable mark
 * 35	'channel edge gradient' mark
 * 36	telephone mark
 * 37	ferry crossing mark
 * 38	marine traffic lights
 * 39	pipeline mark
 * 40	anchorage mark
 * 41	clearing mark	IQ 121;	458;
 * 42	control mark
 * 43	diving mark
 * 44	refuge beacon	IQ 124;
 * 45	foul ground mark
 * 46	yachting mark
 * 47	heliport mark
 * 48	GPS mark
 * 49	seaplane landing mark
 * 50	entry prohibited mark
 * 51	work in progress mark
 * 52	mark with unknown purpose
 * 53	wellhead mark
 * 54	channel separation mark
 * 55	marine farm mark
 * 56	artificial reef mark
 * Remarks:
 * A mark may be a beacon, a buoy, a signpost or may take another form.
 * Value number 38 should be encoded using object class signal station, traffic (SISTAT).
 *
 */
enum class Catspm(val code: Int) {
    FiringDangerAreaMark(1),
    TargetMark(2),
    MarkerShipMark(3),
    DegaussingRangeMark(4),
    BargeMark(5),
    CableMark(6),
    SpoilGroundMark(7),
    OutfallMark(8),
    OceanDataAcquisitionSystem(9),
    RecordingMark(10),
    SeaplaneAnchorageMark(11),
    RecreationZoneMark(12),
    PrivateMark(13),
    MooringMark(14),
    LargeAutomaticNavigationalBuoy(15),
    LeadingMark(16),
    MeasuredDistanceMark(17),
    NoticeMark(18),
    TrafficSeparationSchemeMark(19),
    AnchoringProhibitedMark(20),
    BerthingProhibitedMark(21),
    OvertakingProhibitedMark(22),
    TwoWayTrafficProhibitedMark(23),
    ReducedWakeMark(24),
    SpeedLimitMark(25),
    StopMark(26),
    GeneralWarningMark(27),
    SoundShipsSirenMark(28),
    RestrictedVerticalClearanceM(29),
    MaximumVesselsDraughtMark(30),
    RestrictedHorizontalClearanc(31),
    StrongCurrentWarningMark(32),
    BerthingPermittedMark(33),
    OverheadPowerCableMark(34),
    ChannelEdgeGradientMark(35),
    TelephoneMark(36),
    FerryCrossingMark(37),
    MarineTrafficLights(38),
    PipelineMark(39),
    AnchorageMark(40),
    ClearingMark(41),
    ControlMark(42),
    DivingMark(43),
    RefugeBeacon(44),
    FoulGroundMark(45),
    YachtingMark(46),
    HeliportMark(47),
    GPSMark(48),
    SeaplaneLandingMark(49),
    EntryProhibitedMark(50),
    WorkInProgressMark(51),
    MarkWithUnknownPurpose(52),
    WellheadMark(53),
    ChannelSeparationMark(54),
    MarineFarmMark(55),
    ArtificialReefMark(56);

    companion object {
        fun fromProp(prop: S57Prop?): Catspm? {
            return prop?.let {
                it["CATSPM"]?.toString()?.toIntOrNull()
            }?.let { code ->
                values().firstOrNull { it.code == code }
            }
        }
    }
}

/**
 *
 * Attribute type: E	Used in: <option> AIRARE, BCNCAR, BCNISD, BCNLAT, BCNSAW, BCNSPP, BRIDGE, BUAARE, BUIREL, BUISGL,
 * CAIRNS, CBLOHD, CEMTRY, CHIMNY, COALNE, CONVYR, CRANES, DAMCON, DSHAER, DUNARE, FLASTK, FLGSTF, FLODOC, FNCLNE, FORSTC,
 * HILARE, HULKES, ICEARE, LITFLT, LITVES, LNDELV, LNDMRK, MONUMT, MORFAC, MSTCON, NEWOBJ, OFSPLF, OSPARE, PILPNT, PINGOS,
 * PIPOHD, PONTON, PRDARE, PRDINS, PYLONS, RADDOM, RUNWAY, SILBUI, SILTNK, SLCONS, SLOGRD, SLOTOP, TELPHC, TNKCON, TOWERS,
 * TREPNT, VEGARE, VEGATN, WATFAL, WIMCON, WNDMIL, WRECKS,
 *
 * Expected input:
 * ID	Meaning	INT 1	S-4
 * 1	visually conspicuous	 	340.1;
 * 2	not visually conspicuous
 * Remarks:
 * No remarks.
 */
enum class Conviz(val code: Int) {
    VisuallyConspicuous(1),
    NotVisuallyConspicuous(2);

    companion object {
        fun fromProp(prop: S57Prop?): Conviz? {
            return prop?.let {
                it["CONVIZ"]?.toString()?.toIntOrNull()
            }?.let { code ->
                values().firstOrNull { it.code == code }
            }
        }
    }
}

