package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Parses structured subscription containers into the profile model already used by v2rayNG.
 * Routing rules and unsupported proxy types are deliberately ignored.
 */
internal object StructuredSubscriptionParser {
    private const val MAX_CONTENT_CODE_POINTS = 2_000_000

    fun parse(content: String?): List<ProfileItem> {
        if (content.isNullOrBlank() || content.length > MAX_CONTENT_CODE_POINTS) return emptyList()
        val root = runCatching {
            val options = LoaderOptions().apply {
                maxAliasesForCollections = 20
                codePointLimit = MAX_CONTENT_CODE_POINTS
                isAllowDuplicateKeys = false
            }
            Yaml(SafeConstructor(options)).load<Any>(content)
        }.getOrNull() as? Map<*, *> ?: return emptyList()
        val values = root.normalized()

        return when {
            values.list("proxies") != null -> values.list("proxies")
                .orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.let(::parseProxy) }

            values.long("version") == 1L && values.list("servers") != null -> values.list("servers")
                .orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.let(::parseSip008Server) }

            values.list("outbounds") != null -> values.list("outbounds")
                .orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.let(::parseSingBoxOutbound) }

            else -> emptyList()
        }
    }

    private fun parseSip008Server(raw: Map<*, *>): ProfileItem? {
        val values = raw.normalized()
        return baseProfile(
            type = EConfigType.SHADOWSOCKS,
            values = values,
            portKeys = arrayOf("server_port"),
            nameKeys = arrayOf("remarks", "id"),
        )?.apply {
            method = values.text("method") ?: return null
            password = values.text("password") ?: return null
            applyShadowsocksPlugin(values)
        }
    }

    private fun parseProxy(raw: Map<*, *>): ProfileItem? = parseMappedProfile(raw.normalized(), false)

    private fun parseSingBoxOutbound(raw: Map<*, *>): ProfileItem? = parseMappedProfile(raw.normalized(), true)

    private fun parseMappedProfile(values: Values, singBox: Boolean): ProfileItem? {
        val type = when (values.text("type")?.lowercase()) {
            "ss", "shadowsocks" -> EConfigType.SHADOWSOCKS
            "vmess" -> EConfigType.VMESS
            "vless" -> EConfigType.VLESS
            "trojan" -> EConfigType.TROJAN
            "socks", "socks5" -> EConfigType.SOCKS
            "wireguard" -> EConfigType.WIREGUARD
            "hysteria2", "hy2" -> EConfigType.HYSTERIA2
            else -> return null
        }
        val profile = baseProfile(
            type,
            values,
            portKeys = if (singBox) arrayOf("server_port", "port") else arrayOf("port", "server_port"),
            nameKeys = if (singBox) arrayOf("tag", "name") else arrayOf("name", "tag"),
        ) ?: return null

        when (type) {
            EConfigType.SHADOWSOCKS -> {
                profile.method = values.text("cipher", "method") ?: return null
                profile.password = values.text("password") ?: return null
                profile.applyShadowsocksPlugin(values)
            }

            EConfigType.VMESS, EConfigType.VLESS -> {
                profile.password = values.text("uuid", "id") ?: return null
                profile.method = if (type == EConfigType.VLESS) {
                    values.text("encryption") ?: "none"
                } else {
                    values.text("cipher", "security") ?: AppConfig.DEFAULT_SECURITY
                }
                profile.applyTransport(values, singBox)
            }

            EConfigType.TROJAN -> {
                profile.password = values.text("password") ?: return null
                profile.security = AppConfig.TLS
                profile.applyTransport(values, singBox)
            }

            EConfigType.SOCKS -> {
                profile.username = values.text("username")
                profile.password = values.text("password")
            }

            EConfigType.WIREGUARD -> {
                profile.secretKey = values.text("private-key", "private_key") ?: return null
                profile.publicKey = values.text("public-key", "public_key", "peer_public_key") ?: return null
                profile.preSharedKey = values.text("pre-shared-key", "pre_shared_key")
                profile.localAddress = values.textList("ip", "address", "local_address")
                    ?.joinToString(",")
                    ?: AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
                profile.reserved = values.textList("reserved")?.joinToString(",") ?: "0,0,0"
                profile.mtu = values.int("mtu")
            }

            EConfigType.HYSTERIA2 -> {
                profile.password = values.text("password", "auth", "auth_str") ?: return null
                profile.network = NetworkType.HYSTERIA.type
                profile.security = AppConfig.TLS
                profile.sni = values.text("sni", "server-name", "server_name")
                profile.insecure = values.boolean("skip-cert-verify", "insecure") ?: false
                val obfs = values.map("obfs")
                profile.obfsPassword = values.text("obfs-password", "obfs_password")
                    ?: obfs?.text("password")
                profile.portHopping = values.text("ports")
            }

            else -> return null
        }
        return profile
    }

    private fun baseProfile(
        type: EConfigType,
        values: Values,
        portKeys: Array<String>,
        nameKeys: Array<String>,
    ): ProfileItem? {
        val server = values.text("server") ?: return null
        val port = values.text(*portKeys)?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return ProfileItem.create(type).apply {
            remarks = values.text(*nameKeys) ?: server
            this.server = server.removeSurrounding("[", "]")
            serverPort = port.toString()
        }
    }

    private fun ProfileItem.applyTransport(values: Values, singBox: Boolean) {
        val transport = values.map("transport")
        network = normalizeNetwork(
            if (singBox) transport?.text("type") else values.text("network"),
        )
        val tls = values.map("tls")
        val reality = values.map("reality-opts", "reality_opts")
            ?: tls?.map("reality")?.takeIf { it.boolean("enabled") != false }
        val tlsEnabled = values.boolean("tls") == true || tls?.boolean("enabled") == true
        security = when {
            reality != null -> AppConfig.REALITY
            tlsEnabled -> AppConfig.TLS
            configType == EConfigType.TROJAN -> AppConfig.TLS
            else -> null
        }
        sni = values.text("servername", "sni", "server-name", "server_name")
            ?: tls?.text("server_name", "server-name")
        insecure = values.boolean("skip-cert-verify", "skip_cert_verify")
            ?: tls?.boolean("insecure")
            ?: false
        alpn = values.textList("alpn")?.joinToString(",")
            ?: tls?.textList("alpn")?.joinToString(",")
        fingerPrint = values.text("client-fingerprint", "client_fingerprint")
            ?: tls?.map("utls")?.text("fingerprint")
        flow = values.text("flow")
        publicKey = reality?.text("public-key", "public_key")
        shortId = reality?.text("short-id", "short_id")

        val ws = values.map("ws-opts", "ws_opts")
        val grpc = values.map("grpc-opts", "grpc_opts")
        val http = values.map("h2-opts", "h2_opts", "http-opts", "http_opts")
        path = ws?.text("path") ?: http?.text("path") ?: transport?.text("path")
        host = ws?.map("headers")?.text("host")
            ?: transport?.map("headers")?.text("host")
            ?: http?.textList("host")?.firstOrNull()
        serviceName = grpc?.text("grpc-service-name", "grpc_service_name", "service-name", "service_name")
            ?: transport?.text("service_name", "service-name")
        authority = grpc?.text("authority") ?: transport?.text("host")
    }

    private fun ProfileItem.applyShadowsocksPlugin(values: Values) {
        val plugin = values.text("plugin")?.lowercase() ?: return
        val options = values.text("plugin_opts", "plugin-opts")
            ?.split(';')
            ?.mapNotNull { part ->
                part.split('=', limit = 2).takeIf { it.size == 2 }
                    ?.let { it[0].trim().lowercase() to it[1].trim() }
            }
            ?.toMap()
            .orEmpty()
        when {
            plugin.contains("obfs") && options["obfs"] == "http" -> {
                network = NetworkType.TCP.type
                headerType = "http"
                host = options["obfs-host"] ?: options["host"]
            }

            plugin == "v2ray-plugin" -> {
                network = NetworkType.WS.type
                host = options["host"]
                path = options["path"]
                security = if ("tls" in options || options["mode"] == "tls") AppConfig.TLS else null
            }
        }
    }

    private fun normalizeNetwork(value: String?): String = when (value?.lowercase()) {
        "websocket" -> NetworkType.WS.type
        "httpupgrade", "http-upgrade" -> NetworkType.HTTP_UPGRADE.type
        "http", "h2" -> NetworkType.HTTP.type
        "grpc" -> NetworkType.GRPC.type
        "xhttp", "splithttp" -> NetworkType.XHTTP.type
        "kcp", "mkcp" -> NetworkType.KCP.type
        else -> NetworkType.TCP.type
    }

    private class Values(private val source: Map<String, Any?>) {
        fun text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            source[key.lowercase()]?.let { value ->
                when (value) {
                    is String -> value.trim().takeIf(String::isNotEmpty)
                    is Number -> if (value.toDouble() % 1.0 == 0.0) value.toLong().toString() else value.toString()
                    else -> null
                }
            }
        }

        fun long(vararg keys: String): Long? = text(*keys)?.toLongOrNull()
        fun int(vararg keys: String): Int? = text(*keys)?.toIntOrNull()
        fun boolean(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
            when (val value = source[key.lowercase()]) {
                is Boolean -> value
                is String -> when (value.trim().lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
                else -> null
            }
        }

        fun map(vararg keys: String): Values? = keys.firstNotNullOfOrNull { key ->
            (source[key.lowercase()] as? Map<*, *>)?.normalized()
        }

        fun list(key: String): List<*>? = source[key.lowercase()] as? List<*>

        fun textList(vararg keys: String): List<String>? = keys.firstNotNullOfOrNull { key ->
            when (val value = source[key.lowercase()]) {
                is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
                    .takeIf { it.isNotEmpty() }
                is String -> listOf(value.trim()).takeIf { value.isNotBlank() }
                else -> null
            }
        }
    }

    private fun Map<*, *>.normalized(): Values = Values(entries.associate { entry ->
        entry.key.toString().lowercase() to entry.value
    })
}
