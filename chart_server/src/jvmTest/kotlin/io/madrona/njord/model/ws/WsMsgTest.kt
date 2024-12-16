package io.madrona.njord.model.ws

import kotlinx.serialization.json.Json.Default.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WsMsgTest {

    @Test
    fun encodeContainsType() {
        val info: WsMsg = WsMsg.Info(1, 2, "foo")
        val s = encodeToString(WsMsg.serializer(), info)
        assertTrue(s.contains("\"type\":\"io.madrona.njord.model.ws.WsMsg.Info\""))
    }

}