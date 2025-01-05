package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.AdminResponse
import io.madrona.njord.model.AdminSignature
import io.madrona.njord.util.logger
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.until
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun ApplicationCall.isAdminAuthorized(): Boolean {
    return principal<UserIdPrincipal>()?.name == Singletons.config.adminUser
}

class AdminHandler(
    private val util: AdminUtil = Singletons.adminUtil,
) : KtorHandler {
    override val route = "/v1/admin"

    /**
     * It's up to you to protect this endpoint if deployed in a public environment.
     * Returns an authorization signature for other mutating calls that require a valid signature.
     */
    override suspend fun handleGet(call: ApplicationCall) {
        if (call.isAdminAuthorized()) {
            call.respond(util.createSignatureResponse())
        } else {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }
}

class AdminUtil(
    private val config: ChartsConfig = Singletons.config,
) {
    val log = logger()

    fun createSignatureResponse(): AdminResponse {
        val signature = createSignature()
        return AdminResponse(
            signature = signature,
            signatureEncoded = URLEncoder.encode(encodeToString(AdminSignature.serializer(), signature).encodeBase64(), StandardCharsets.UTF_8)
        )
    }

    private fun createSignature(): AdminSignature {
        val now = Clock.System.now()
        val expiration = now.plus(config.adminExpirationSeconds.toDuration(DurationUnit.SECONDS))
        val dateString = now.toString()
        val secretKey = SecretKeySpec(config.adminKey.toByteArray(), "HmacSHA256")
        val uuid = UUID.randomUUID().toString()
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(secretKey)
        hmac.update(config.externalBaseUrl.toByteArray())
        hmac.update(dateString.toByteArray())
        hmac.update(uuid.toByteArray())
        val signature = Base64.getUrlEncoder().encodeToString(hmac.doFinal())
        return AdminSignature(
            date = now,
            signature = signature,
            uuid = uuid,
            expirationDate = expiration
        )
    }

    fun verifySignature(query: String): Boolean {
        val data = URLDecoder.decode(query, StandardCharsets.UTF_8).decodeBase64String()
        val sig = decodeFromString<AdminSignature>(data)
        return verifySignature(sig)
    }

    fun verifySignature(signature: AdminSignature): Boolean {
        val now = Clock.System.now()
        val then = signature.date
        val elapsed = then.until(now, DateTimeUnit.SECOND)
        log.info("then=$then now=$now elapsed=$elapsed max=${config.adminExpirationSeconds}")
        if (elapsed > config.adminExpirationSeconds) {
            return false
        }
        val secretKey = SecretKeySpec(config.adminKey.toByteArray(), "HmacSHA256")
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(secretKey)
        hmac.update(config.externalBaseUrl.toByteArray())
        hmac.update(signature.date.toString().toByteArray())
        hmac.update(signature.uuid.toByteArray())
        val checkSignature = Base64.getUrlEncoder().encodeToString(hmac.doFinal())
        val match = checkSignature.equals(signature.signature)
        log.info("match=$match expected=$checkSignature got=${signature.signature}")
        return match
    }
}

suspend fun ApplicationCall.requireSignature(onAuthorized: suspend () -> Unit) {
    val adminUti = Singletons.adminUtil
    val valid = request.queryParameters["signature"]?.let {
        adminUti.verifySignature(it)
    } ?: false
    if (valid) {
        onAuthorized()
    } else {
        respond(HttpStatusCode.Unauthorized)
    }
}
