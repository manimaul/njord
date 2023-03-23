package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.ext.KtorHandler

class FeatureHandler(
    private val featureDao: FeatureDao = FeatureDao(),
) : KtorHandler {
    override val route = "/v1/feature"

    override suspend fun handleGet(call: ApplicationCall) {
        call.request.queryParameters["layer"]?.let { layer ->
            featureDao.findLayerPositions(layer)?.let {
                call.respond(it)
            }
        } ?: call.request.queryParameters["lnam"]?.let {
            featureDao.findFeature(it)?.let { record ->
                call.respond(record)
            }
        } ?: call.respond(HttpStatusCode.NotFound)
    }
}