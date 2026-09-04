package com.v2ray.ang.handler

import android.text.TextUtils
import android.util.Base64
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.util.Base64 as JavaBase64

/** Real URI parser regression tests; no Android storage or network is initialized. */
class AccountConfigTest {
    private lateinit var base64: MockedStatic<Base64>
    private lateinit var textUtils: MockedStatic<TextUtils>

    @Before
    fun setUp() {
        base64 = Mockito.mockStatic(Base64::class.java)
        base64.`when`<ByteArray> { Base64.decode(Mockito.anyString(), Mockito.anyInt()) }
            .thenAnswer { call ->
                val flags = call.arguments[1] as Int
                val decoder = if (flags and Base64.URL_SAFE != 0) JavaBase64.getUrlDecoder()
                    else JavaBase64.getDecoder()
                // Invalid/unsupported text decodes to no configs in these storage-free tests.
                runCatching { decoder.decode(call.arguments[0] as String) }.getOrDefault(byteArrayOf())
            }
        base64.`when`<String> { Base64.encodeToString(Mockito.any(ByteArray::class.java), Mockito.anyInt()) }
            .thenAnswer { call -> JavaBase64.getEncoder().encodeToString(call.arguments[0] as ByteArray) }
        textUtils = Mockito.mockStatic(TextUtils::class.java)
        textUtils.`when`<Boolean> { TextUtils.isEmpty(Mockito.any()) }
            .thenAnswer { (it.arguments[0] as CharSequence?).isNullOrEmpty() }
    }

    @After
    fun tearDown() {
        textUtils.close()
        base64.close()
    }

    @Test
    fun configSchemesDoNotGoThroughSubscriptionUrlValidation() {
        assertTrue(AngConfigManager.isSubscriptionInput(" https://example.com/sub#Account "))
        assertTrue(AngConfigManager.isSubscriptionInput("http://127.0.0.1/sub"))
        listOf("vless", "vmess", "trojan", "ss", "socks", "wireguard", "hysteria2", "hy2").forEach {
            assertFalse(AngConfigManager.isSubscriptionInput("$it://config"))
        }
        assertFalse(AngConfigManager.isSubscriptionInput(""))
    }

    @Test
    fun vlessLinkImportsIntoManualAccountWithAllConnectionFields() {
        val profile = AngConfigManager.parseStandaloneConfigs("  $VLESS  ").single()
        assertEquals(EConfigType.VLESS, profile.configType)
        assertEquals(AppConfig.DEFAULT_SUBSCRIPTION_ID, profile.subscriptionId)
        assertEquals("one.example.com", profile.server)
        assertEquals("443", profile.serverPort)
        assertEquals(UUID, profile.password)
        assertEquals("ws", profile.network)
        assertEquals("/tunnel", profile.path)
        assertEquals("tls", profile.security)
        assertEquals("cdn.example.com", profile.sni)
        assertEquals("حساب من", profile.remarks)
    }

    @Test
    fun trojanLinkAndBase64BatchImportInOriginalOrder() {
        val encoded = JavaBase64.getEncoder().encodeToString("$VLESS\n$TROJAN".toByteArray())
        val profiles = AngConfigManager.parseStandaloneConfigs(encoded)
        assertEquals(listOf(EConfigType.VLESS, EConfigType.TROJAN), profiles.map { it.configType })
        assertEquals("secret", profiles[1].password)
        assertEquals("two.example.com", profiles[1].server)
        assertTrue(profiles.all { it.subscriptionId == AppConfig.DEFAULT_SUBSCRIPTION_ID })
    }

    @Test
    fun vmessLinkImports() {
        val json = """{"v":"2","ps":"VMess","add":"vmess.example.com","port":"443","id":"$UUID","aid":"0","scy":"auto","net":"tcp","type":"none","host":"","path":"","tls":"tls"}"""
        val link = "vmess://" + JavaBase64.getEncoder().encodeToString(json.toByteArray())
        val profile = AngConfigManager.parseStandaloneConfigs(link).single()
        assertEquals(EConfigType.VMESS, profile.configType)
        assertEquals("vmess.example.com", profile.server)
        assertEquals(UUID, profile.password)
    }

    @Test
    fun whitespaceBomAndDuplicateLinesAreHandled() {
        val profiles = AngConfigManager.parseStandaloneConfigs("\uFEFF$VLESS\r\n  $TROJAN \r\n\n$VLESS")
        assertEquals(2, profiles.size)
        assertEquals(listOf("one.example.com", "two.example.com"), profiles.map { it.server })
    }

    @Test
    fun invalidInputDoesNotCreateAnAccountOrTouchStorage() {
        listOf("", "  ", "not a config", "https://example.com/sub", "ftp://example.com/file").forEach {
            assertEquals(0, AngConfigManager.importStandaloneConfigs(it, "Manual configs"))
        }
    }

    @Test
    fun manualShareLinksCanBeImportedAgainWithoutLosingCredentials() {
        val profiles = AngConfigManager.parseStandaloneConfigs("$VLESS\n$TROJAN")
        val shared = profiles.joinToString("\n") { AngConfigManager.shareConfig(it) }
        val restored = AngConfigManager.parseStandaloneConfigs(shared)
        assertEquals(2, restored.size)
        profiles.zip(restored).forEach { (original, copy) ->
            assertEquals(original.configType, copy.configType)
            assertEquals(original.server, copy.server)
            assertEquals(original.serverPort, copy.serverPort)
            assertEquals(original.password, copy.password)
            assertEquals(original.path, copy.path)
            assertEquals(original.sni, copy.sni)
            assertEquals(original.remarks, copy.remarks)
        }
    }

    @Test
    fun unsupportedProfilesAreNotSharedAsBogusLinks() {
        assertEquals("", AngConfigManager.shareConfig(ProfileItem.create(EConfigType.CUSTOM)))
    }

    companion object {
        private const val UUID = "00000000-0000-4000-8000-000000000001"
        private const val VLESS = "vless://$UUID@one.example.com:443?encryption=none&type=ws&security=tls&sni=cdn.example.com&path=%2Ftunnel#%D8%AD%D8%B3%D8%A7%D8%A8%20%D9%85%D9%86"
        private const val TROJAN = "trojan://secret@two.example.com:443#Second"
    }
}
