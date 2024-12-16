package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.AdminSignature

class AdminVerifyHandler(
    private val util: AdminUtil = Singletons.adminUtil,
) : KtorHandler {
    override val route = "/v1/admin/verify"

    override suspend fun handlePost(call: ApplicationCall) {
        val signature = call.receive<AdminSignature>()
        if (util.verifySignature(signature)) {
            call.respond(util.createSignatureResponse())
        } else {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }
}

