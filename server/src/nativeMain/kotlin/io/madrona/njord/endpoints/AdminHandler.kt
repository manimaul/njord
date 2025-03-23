package io.madrona.njord.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.core.toByteArray
import io.madrona.njord.ChartsConfig
import io.madrona.njord.Singletons
import io.madrona.njord.ext.KtorHandler
import io.madrona.njord.model.AdminResponse
import io.madrona.njord.model.AdminSignature
import io.madrona.njord.util.UUID
import io.madrona.njord.util.logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.usePinned
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.until
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import libssl.EVP_sha256
import libssl.HMAC_CTX_free
import libssl.HMAC_CTX_new
import libssl.HMAC_Final
import libssl.HMAC_Init_ex
import libssl.HMAC_Update
import kotlin.io.encoding.Base64
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
            signatureEncoded = encodeToString(AdminSignature.serializer(), signature).encodeBase64().encodeURLPath(encodeSlash = true)
        )
    }

    private fun createSignature(): AdminSignature {
        val now = Clock.System.now()
        val expiration = now.plus(config.adminExpirationSeconds.toDuration(DurationUnit.SECONDS))
        val dateString = now.toString()
        val uuid = UUID.randomUUID().toString()
        val hmac = HmacSHA256(config.adminKey)
        hmac.update(config.externalBaseUrl.toByteArray())
        hmac.update(dateString.toByteArray())
        hmac.update(uuid.toByteArray())
        val signature = hmac.doFinal().encodeBase64()
        return AdminSignature(
            date = now,
            signature = signature,
            uuid = uuid,
            expirationDate = expiration
        )
    }

    fun verifySignature(query: String): Boolean {
        val data = query.decodeURLPart().decodeBase64String()
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
        val hmac = HmacSHA256(config.adminKey)
        hmac.update(config.externalBaseUrl.toByteArray())
        hmac.update(signature.date.toString().toByteArray())
        hmac.update(signature.uuid.toByteArray())
        val checkSignature = hmac.doFinal().encodeBase64()
        val match = checkSignature == signature.signature
        log.info("match=$match expected=$checkSignature got=${signature.signature}")
        return match
    }
}


@OptIn(ExperimentalForeignApi::class)
class HmacSHA256(val key: String) {
    private val ctx = HMAC_CTX_new()
    private val keyBytes = key.encodeToByteArray()

    init {
        keyBytes.usePinned { pinnedKey ->
            HMAC_Init_ex(
                ctx,
                pinnedKey.addressOf(0),
                keyBytes.size,
                EVP_sha256(),
                null
            )
        }
    }

    fun update(data: ByteArray) {
        data.usePinned { pinnedData ->
            HMAC_Update(ctx, pinnedData.addressOf(0).reinterpret(), data.size.toULong())
        }
    }

    fun doFinal(): ByteArray {
        val result = ByteArray(32)
        val len = uintArrayOf(0u).toCValues()

        result.usePinned { pinnedResult ->
            HMAC_Final(ctx, pinnedResult.addressOf(0).reinterpret(), len)
        }

        HMAC_CTX_free(ctx)
        return result
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
