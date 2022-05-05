package io.madrona.njord.layers.set

import io.madrona.njord.layers.Layerable

/**
 * airport / airfield (AIRARE)
 * anchor berth (ACHBRT)
 * anchor (ACHPNT) deleted
 * anchorage area (ACHARE)
 * beacon cardinal (BCNCAR)
 * beacon isolated danger (BCNISD)
 * beacon lateral (BCNLAT)
 * beacon safe water (BCNSAW)
 * beacon special purpose/general (BCNSPP)
 * building single (BUISGL)
 * building religious (BUIREL) deleted
 * built-up area (BUAARE)
 * buoy cardinal
 * buoy installation
 * buoy isolated danger
 * buoy lateral
 * buoy safe water
 * buoy special purpose/general
 * cable area
 * cable submarine
 * cargo transshipment area
 * causeway
 * caution area
 * chain/wire
 * crane
 * daymark
 * deep water route part
 * dredge area
 * dumping ground
 * dyke
 * fairway
 * fence/wall
 * ferry route
 * fishing ground
 * fog signal
 * fortified structure
 * gate
 * hulk
 * incineration area
 * inshore traffic zone
 * lake shore
 * land region
 * landmark
 * light
 * light extinguished
 * light float
 * light vessel
 * marine farm/culture
 * military practice area
 * monument
 * navigation line
 * offshore production area
 * pile
 * pilot boarding place
 * pipeline area
 * precaustion area
 * production / storage area
 * radar line
 * radar range
 * radar reflector
 * radar transpoder beacon
 * radio calling-in point
 * recommended route centerline
 * recommended track
 * recommended traffic lane part
 * restricted area
 * retro-reflector
 * river
 * runway
 * sand waves
 * sea area / named water area
 * sea-plane landing area
 * signal station traffic
 * signal station arning
 * silo / tank
 * slopeing ground
 * submarine transit lane
 * swept area
 * text
 * topmark
 * tower
 * traffic separation line
 * traffic separation scheme boundary
 * traffic separation scheme crossing
 * traffic separation lane part
 * traffic separation roundabout
 * traffic separation separation zone
 * tunnel
 * two-way route part
 * unsurveyed area
 * zero meter contour
 * new object
 * navigational system of marks
 * cartographic symbol
 * text
 * bunker station
 * beacon water-way
 * buoy water-way
 * refuse dump
 * terminal
 * turning basin
 * waterway guage
 * vehicle transfer
 * annotation
 * general boundary
 * traffic line
 * general navaid
 */
class StandardLayers {
    val layers = sequenceOf<Layerable>()
}
