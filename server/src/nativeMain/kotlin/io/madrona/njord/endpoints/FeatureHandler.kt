package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.madrona.njord.db.FeatureDao
import io.madrona.njord.ext.KtorHandler

class FeatureHandler(
    private val featureDao: FeatureDao = FeatureDao(),
) : KtorHandler {
    override val route = "/v1/feature/{by}/{arg}"


    override suspend fun handleGet(call: ApplicationCall) {
        when (call.parameters["by"]) {
            "layer" -> call.respondLayer()
            "lnam" -> call.respondLnam()
            else -> call.respond(HttpStatusCode.NotFound)
        }

    }

    private suspend fun ApplicationCall.respondLayer() {
        val id = request.queryParameters["start_id"]?.toLongOrNull() ?: 0L
        parameters["arg"]?.let { layer ->
            featureDao.findLayerPositionsPage(layer, id)?.let {
                respond(it)
            }
        }  ?: respond(HttpStatusCode.NotFound)
    }

    private suspend fun ApplicationCall.respondLnam() {
        parameters["arg"]?.let {
            featureDao.findFeature(it)?.let { record ->
                respond(record)
            }
        } ?: respond(HttpStatusCode.NotFound)
    }
}