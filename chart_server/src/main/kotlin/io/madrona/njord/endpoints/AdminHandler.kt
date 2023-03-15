package io.madrona.njord.endpoints

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.ext.respondJson
import io.madrona.njord.model.AdminSignature
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * It's up to you to protect this endpoint
 */
class AdminHandler(
    private val util: AdminUtil = AdminUtil()
) : KtorHandler {
    override val route = "/v1/admin"
    override suspend fun handleGet(call: ApplicationCall) {
        call.respondJson(util.createSignature())
    }

    override suspend fun handlePost(call: ApplicationCall) {
        val signature = call.receive<AdminSignature>()
        if (util.verifySignature(signature)) {
            call.respond(util.createSignature())
        } else {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }
}

class AdminUtil(
    private val config: ChartsConfig = Singletons.config,
    private val objectMapper: ObjectMapper = Singletons.objectMapper
) {
    private var formatter = DateTimeFormatter.ISO_INSTANT

    fun createSignature(): AdminSignature {
        val now = Instant.now()
        val expiration = now.plus(config.adminExpirationSeconds, ChronoUnit.SECONDS)
        val dateString = formatter.format(now)
        val expirationString = formatter.format(expiration)
        val secretKey = SecretKeySpec(config.adminKey.toByteArray(), "HmacSHA256")
        val uuid = UUID.randomUUID().toString()
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(secretKey)
        hmac.update(config.externalBaseUrl.toByteArray())
        hmac.update(dateString.toByteArray())
        hmac.update(uuid.toByteArray())
        val signature = Base64.getEncoder().encodeToString(hmac.doFinal())
        return AdminSignature(
            date = dateString,
            signature = signature,
            uuid = uuid,
            expirationDate = expirationString
        )
    }

    fun veryifySignature(query: String): Boolean {
        val data = Base64.getDecoder().decode(query)
        return verifySignature(objectMapper.readValue(data))
    }

    fun verifySignature(signature: AdminSignature): Boolean {
        val now = Instant.now()
        val then = Instant.from(formatter.parse(signature.date))
        if (now.epochSecond - then.epochSecond > config.adminExpirationSeconds) {
            return false
        }
        val secretKey = SecretKeySpec(config.adminKey.toByteArray(), "HmacSHA256")
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(secretKey)
        hmac.update(config.externalBaseUrl.toByteArray())
        hmac.update(signature.date.toByteArray())
        hmac.update(signature.uuid.toByteArray())
        val checkSignature = Base64.getEncoder().encodeToString(hmac.doFinal())
        return checkSignature.equals(signature.signature)
    }
}
