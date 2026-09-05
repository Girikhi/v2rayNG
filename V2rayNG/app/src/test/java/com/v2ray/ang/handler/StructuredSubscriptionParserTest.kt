package com.v2ray.ang.handler

import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredSubscriptionParserTest {
    @Test
    fun parsesCompatibleMihomoYamlProxiesAndIgnoresUnsupportedEntries() {
        val content = """
            proxies:
              - name: SS account
                type: ss
                server: ss.example.com
                port: 8388
                cipher: chacha20-ietf-poly1305
                password: secret
              - name: VLESS WS
                type: vless
                server: edge.example.com
                port: 443
                uuid: 00000000-0000-4000-8000-000000000001
                network: ws
                tls: true
                servername: cdn.example.com
                ws-opts:
                  path: /tunnel
                  headers:
                    Host: cdn.example.com
              - name: Built in
                type: direct
                server: ignored.example.com
                port: 80
        """.trimIndent()

        val profiles = StructuredSubscriptionParser.parse(content)

        assertEquals(2, profiles.size)
        assertEquals(EConfigType.SHADOWSOCKS, profiles[0].configType)
        assertEquals("chacha20-ietf-poly1305", profiles[0].method)
        assertEquals(EConfigType.VLESS, profiles[1].configType)
        assertEquals("ws", profiles[1].network)
        assertEquals("tls", profiles[1].security)
        assertEquals("/tunnel", profiles[1].path)
        assertEquals("cdn.example.com", profiles[1].host)
        assertEquals("cdn.example.com", profiles[1].sni)
    }

    @Test
    fun parsesOfficialSip008Json() {
        val content = """
            {
              "version": 1,
              "servers": [{
                "id": "27b8a625-4f4b-4428-9f0f-8a2317db7c79",
                "remarks": "SIP008 server",
                "server": "ss.example.com",
                "server_port": 8388,
                "password": "secret",
                "method": "aes-256-gcm"
              }]
            }
        """.trimIndent()

        val profile = StructuredSubscriptionParser.parse(content).single()

        assertEquals(EConfigType.SHADOWSOCKS, profile.configType)
        assertEquals("SIP008 server", profile.remarks)
        assertEquals("ss.example.com", profile.server)
        assertEquals("8388", profile.serverPort)
        assertEquals("aes-256-gcm", profile.method)
        assertEquals("secret", profile.password)
    }

    @Test
    fun parsesCompatibleSingBoxOutboundFields() {
        val content = """
            {
              "outbounds": [
                {
                  "type": "vless",
                  "tag": "sing-box WS",
                  "server": "edge.example.com",
                  "server_port": 443,
                  "uuid": "00000000-0000-4000-8000-000000000001",
                  "transport": {
                    "type": "ws",
                    "path": "/socket",
                    "headers": { "Host": "cdn.example.com" }
                  },
                  "tls": {
                    "enabled": true,
                    "server_name": "cdn.example.com",
                    "insecure": false
                  }
                },
                { "type": "direct", "tag": "direct" }
              ]
            }
        """.trimIndent()

        val profile = StructuredSubscriptionParser.parse(content).single()

        assertEquals(EConfigType.VLESS, profile.configType)
        assertEquals("sing-box WS", profile.remarks)
        assertEquals("ws", profile.network)
        assertEquals("/socket", profile.path)
        assertEquals("cdn.example.com", profile.host)
        assertEquals("cdn.example.com", profile.sni)
        assertFalse(profile.insecure ?: true)
    }

    @Test
    fun rejectsUnrelatedOrMalformedStructuredContent() {
        assertTrue(StructuredSubscriptionParser.parse("not a subscription").isEmpty())
        assertTrue(StructuredSubscriptionParser.parse("proxies: [broken").isEmpty())
        assertTrue(StructuredSubscriptionParser.parse("""{"servers": []}""").isEmpty())
        assertTrue(StructuredSubscriptionParser.parse("""{"version": 1, "servers": [{"server":"missing-port"}]}""").isEmpty())
    }
}
