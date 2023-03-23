package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.db.ChartDao
import io.madrona.njord.db.GeoJsonDao
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.letTwo
import io.madrona.njord.model.FeatureInsert
import mil.nga.sf.geojson.GeoJsonObject

class GeoJsonHandler(
    private val geoJsonDao: GeoJsonDao = GeoJsonDao(),
    private val chartDao: ChartDao = ChartDao(),
) : KtorHandler {
    override val route = "/v1/geojson"

    override suspend fun handleGet(call: ApplicationCall) {
        letTwo(
            call.request.queryParameters["chart_id"]?.toLongOrNull(),
            call.request.queryParameters["layer_name"]
        ) { id, name ->
            geoJsonDao.fetchAsync(id, name)
        }?.let {
            call.respond(it)
        } ?: call.respond(HttpStatusCode.NotFound)
    }

    override suspend fun handlePost(call: ApplicationCall) = call.requireSignature {
        val geo = call.receive<GeoJsonObject>()
        letTwo(
            call.request.queryParameters["chart_id"]?.toLongOrNull(),
            call.request.queryParameters["layer_name"]
        ) { id, name ->
            chartDao.findAsync(id)?.let { chart ->
                FeatureInsert(
                    name,
                    chart,
                    geo
                )
            }
        }?.let { fi ->
            geoJsonDao.insertAsync(fi)?.let { it to fi }
        }?.let {
            if (it.first > 0) {
                call.respond(HttpStatusCode.OK, it)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
}
