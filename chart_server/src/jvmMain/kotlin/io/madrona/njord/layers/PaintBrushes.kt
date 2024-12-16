package io.madrona.njord.layers

sealed interface Symbol {
    var property: Any?

    class Sprite(sprite: io.madrona.njord.layers.Sprite) : Symbol {
        override var property: Any? = sprite
    }

    class Property(num: Int? = null) : Symbol {
        override var property: Any? = listOf("get", num?.let { "SY$it" } ?: "SY");
    }
}

sealed interface Label {
    var property: Any?

    class Text(text: String) : Label {
        override var property: Any? = text
    }

    class Property(name: String) : Label {
        override var property: Any? = listOf("get", name);
    }
}

sealed interface IconRot {
    val property: Any?

    data class Degrees(val deg: Float) : IconRot {
        override val property = deg
    }

    class Property(name: String) : IconRot {
        override var property = listOf("get", name);
    }
}
//sealed interface IconRotAlign {
//    val property: Any?
//
//    object Map : IconRotAlign {
//        override val property = IconRotationAlignment.MAP
//    }
//    object ViewPort: IconRotAlign {
//        override val property = IconRotationAlignment.VIEWPORT
//    }
//    object Auto: IconRotAlign {
//        override val property = IconRotationAlignment.AUTO
//    }
//    data class IfValueEq(
//        val key: String,
//        val value: String,
//        val eq: IconRotationAlignment,
//        val nEq: IconRotationAlignment,
//    ) : IconRotAlign {
//        override val property: Any
//            get() = listOf(
//                "case",
//                listOf("==", listOf("get", key), value),
//                listOf("literal", eq),
//                listOf("literal", nEq)
//            )
//    }
//}

sealed interface Offset {
    val property: Any?

    data class Coord(val x: Float = 0f, val y: Float = 0f) : Offset {
        override val property: List<Float> = listOf(x, y)
    }

    class EvalEq(key: String, value: String, eq: Coord, neq: Coord) : Offset {
        override val property = listOf(
            "case",
            listOf("==", listOf("get", key), value),
            listOf("literal", eq.property),
            listOf("literal", neq.property)
        )
    }
}

sealed interface LineStyle {
    var lineDashArray: List<Float>?

    object DashLine : LineStyle {
        override var lineDashArray: List<Float>? = listOf(1f, 2f)
    }

    object Solid : LineStyle {
        override var lineDashArray: List<Float>? = null
    }

    class CustomDash(width: Float, gap: Float) : LineStyle {
        override var lineDashArray: List<Float>? = listOf(width, gap)
    }
}

enum class Color {
    NODTA,
    CURSR,
    CHBLK,
    CHGRD,
    CHGRF,
    CHRED,
    CHGRN,
    CHYLW,
    CHMGD,
    CHMGF,
    CHBRN,
    CHWHT,
    SCLBR,
    CHCOR,
    LITRD,
    LITGN,
    LITYW,
    ISDNG,
    DNGHL,
    TRFCD,
    TRFCF,
    LANDA,
    LANDF,
    CSTLN,
    SNDG1,
    SNDG2,
    DEPSC,
    DEPCN,
    DEPDW,
    DEPMD,
    DEPMS,
    DEPVS,
    DEPIT,
    RADHI,
    RADLO,
    ARPAT,
    NINFO,
    RESBL,
    ADINF,
    RESGR,
    SHIPS,
    PSTRK,
    SYTRK,
    PLRTE,
    APLRT,
    UINFD,
    UINFF,
    UIBCK,
    UIAFD,
    UINFR,
    UINFG,
    UINFO,
    UINFB,
    UINFM,
    UIBDR,
    UIAFF,
    OUTLW,
    OUTLL,
    RES01,
    RES02,
    RES03,
    BKAJ1,
    BKAJ2,
    MARBL,
    MARCY,
    MARMG,
    MARWH,
}

enum class Sprite {
    ACHARE02,
    ACHARE51,
    ACHBRT07,
    ACHRES51,
    AIRARE02,
    BCNCAR01,
    BCNCAR02,
    BCNCAR03,
    BCNCAR04,
    BCNDEF13,
    BCNISD21,
    BCNLAT15,
    BCNLAT16,
    BCNLAT21,
    BCNLAT22,
    BCNSAW13,
    BCNSAW21,
    BCNSPP13,
    BCNSPP21,
    BOYCAR01,
    BOYCAR02,
    BOYCAR03,
    BOYCAR04,
    BOYDEF03,
    BOYISD12,
    BOYLAT13,
    BOYLAT14,
    BOYLAT23,
    BOYLAT24,
    BOYMOR11,
    BOYSAW12,
    BOYSPP11,
    BOYSPP15,
    BOYSPP25,
    BOYSPP35,
    BOYSUP02,
    BRIDGE01,
    BRTHNO01,
    BUAARE02,
    BUIREL01,
    BUIREL04,
    BUIREL05,
    BUIREL13,
    BUIREL14,
    BUIREL15,
    BUISGL01,
    BUISGL11,
    CAIRNS01,
    CAIRNS11,
    CBLARE51,
    CGUSTA02,
    CHIMNY01,
    CHIMNY11,
    CHINFO06,
    CHINFO07,
    CHINFO08,
    CHINFO09,
    CHINFO10,
    CHINFO11,
    CHKPNT01,
    CRANES01,
    CTYARE51,
    CURDEF01,
    CURENT01,
    DANGER01,
    DANGER02,
    DANGER03,
    DAYSQR01,
    DAYTRI01,
    DAYTRI05,
    DISMAR06,
    DNGHILIT,
    DOMES001,
    DOMES011,
    DSHAER11,
    DWRUTE51,
    EBBSTR01,
    ENTRES51,
    FLASTK01,
    FLASTK11,
    FLDSTR01,
    FLGSTF01,
    FLTHAZ02,
    FOGSIG01,
    FORSTC01,
    FORSTC11,
    FOULAR01P,
    FOULGND1,
    FRYARE51,
    FSHFAC02,
    FSHFAC03,
    FSHFAC03P,
    FSHFAC04P,
    FSHGRD01,
    FSHHAV01,
    FSHHAV02P,
    GATCON03,
    GATCON04,
    HILTOP01,
    HILTOP11,
    HRBFAC09,
    HULKES01,
    ICEARE04P,
    ISODGR01,
    LIGHTS11,
    LIGHTS12,
    LIGHTS13,
    LIGHTS81,
    LIGHTS82,
    LITDEF11,
    LITFLT02,
    LITVES02,
    LNDARE01,
    LOCMAG01,
    LOWACC01,
    MAGVAR01,
    MARCUL02,
    MARCUL02P,
    MARSHES1P,
    MONUMT12,
    MORFAC03,
    MORFAC04,
    MSTCON04,
    MSTCON14,
    NEWOBJ01,
    NOTBRD11,
    NODATA03P,
    OBSTRN01,
    OBSTRN02,
    OBSTRN03,
    OBSTRN11,
    OFSPLF01,
    PILBOP02,
    PILPNT02,
    POSGEN01,
    POSGEN03,
    POSGEN04,
    PRCARE12,
    PRDINS02,
    PRTSUR01P,
    QUARRY01,
    QUESMRK1,
    QUESMRK1P,
    RACNSP01,
    RADRFL03,
    RASCAN01,
    RASCAN11,
    RCLDEF01,
    RCTLPT52,
    RDOCAL02,
    RDOCAL03,
    RDOSTA02,
    RECDEF51,
    RECTRC55,
    RECTRC56,
    RECTRC57,
    RECTRC58,
    RETRFL02,
    RFNERY11,
    ROLROL01,
    RSCSTA02,
    RTLDEF51,
    RTPBCN02,
    SILBUI01,
    SILBUI11,
    SISTAT03,
    SISTAW03,
    SMCFAC02,
    SNDWAV01P,
    SNDWAV02,
    SPRING02,
    TIDEHT01,
    TIDSTR01,
    TMARDEF1,
    TMARDEF2,
    TMBYRD01,
    TNKCON02,
    TNKCON12,
    TNKFRM11,
    TOPMAR02, TOPMAR08, TOPMAR16, TOPMAR25, TOPMAR32, TOPMAR85,
    TOPMAR04, TOPMAR10, TOPMAR17, TOPMAR26, TOPMAR33, TOPMAR86,
    TOPMAR05, TOPMAR12, TOPMAR18, TOPMAR27, TOPMAR34, TOPMAR87,
    TOPMAR06, TOPMAR13, TOPMAR22, TOPMAR28, TOPMAR36, TOPMAR88,
    TOPMAR07, TOPMAR14, TOPMAR24, TOPMAR30, TOPMAR65, TOPMAR89,
    TOWERS01, TOWERS02, TOWERS03, TOWERS12,
    TSSLPT51,
    TWRDEF51,
    TWRTPT52,
    TWRTPT53,
    VEGATN03P,
    VEGATN04P,
    WATTUR02,
    WEDKLP03,
    WIMCON01,
    WIMCON11,
    WNDFRM61,
    WNDMIL12,
}
