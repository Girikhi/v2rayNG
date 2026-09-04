package com.v2ray.ang.handler

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.ManualConfigMode
import com.v2ray.ang.util.JsonUtil
import java.util.UUID

/** Per-profile options: never mutate the user's global Fragment or DNS settings. */
object ManualConfigModes {
    const val GOOGLE_DOH_URL = "https://dns.google/dns-query"
    private val linkTypes = setOf(EConfigType.VLESS, EConfigType.VMESS, EConfigType.TROJAN,
        EConfigType.SHADOWSOCKS, EConfigType.SOCKS, EConfigType.HTTP, EConfigType.WIREGUARD, EConfigType.HYSTERIA2)
    private val udpTransports = setOf("kcp", "quic", "hysteria")

    fun isManual(profile: ProfileItem): Boolean = profile.subscriptionId == AppConfig.DEFAULT_SUBSCRIPTION_ID
    fun hasMode(profile: ProfileItem): Boolean = profile.manualMode != null
    fun supportsModes(profile: ProfileItem): Boolean = profile.configType in linkTypes

    fun usesGoogleDns(profile: ProfileItem): Boolean =
        profile.manualMode == ManualConfigMode.GOOGLE_DOH

    fun variants(profile: ProfileItem, sourceId: String): List<ProfileItem> =
        ManualConfigMode.entries.map { mode ->
            profile.copy(manualMode = mode, manualSourceId = sourceId)
        }

    /** Expand share-link profiles downloaded from a subscription without changing their names or account. */
    fun expandSubscriptionProfiles(profiles: List<ProfileItem>): List<ProfileItem> =
        profiles.flatMap { profile ->
            if (!isManual(profile) && supportsModes(profile)) {
                variants(profile, UUID.randomUUID().toString())
            } else {
                listOf(profile)
            }
        }

    /** Preserve original GUIDs, names, settings and selection; repair missing variants idempotently. */
    fun completeModes(existing: List<ServersCache>): List<ServersCache> {
        val normalized = existing.map { item ->
            // Raw JSON and composite profiles are not standalone links; leave their storage untouched.
            if (isManual(item.profile) && item.profile.configType in linkTypes) {
                item.copy(profile = item.profile.copy(
                    manualMode = item.profile.manualMode ?: ManualConfigMode.ORIGINAL,
                    manualSourceId = item.profile.manualSourceId ?: item.guid,
                ))
            } else item
        }
        val presentModes = normalized.filter { isManual(it.profile) }
            .groupBy { it.profile.manualSourceId }
            .mapValues { (_, items) -> items.map { it.profile.manualMode }.toMutableSet() }
        return buildList {
            normalized.forEach { original ->
                add(original)
                if (isManual(original.profile) && original.profile.configType in linkTypes &&
                    original.profile.manualMode == ManualConfigMode.ORIGINAL
                ) {
                    val sourceId = requireNotNull(original.profile.manualSourceId)
                    val present = presentModes.getValue(sourceId)
                    variants(original.profile, sourceId).forEach { variant ->
                        if (present.add(variant.manualMode)) {
                            add(ServersCache(UUID.randomUUID().toString(), variant))
                        }
                    }
                }
            }
        }
    }

    fun <T> failuresLast(items: List<T>, delay: (T) -> Long): List<T> =
        items.sortedBy { delay(it) < 0L }

    fun supportsFragment(profile: ProfileItem): Boolean =
        profile.configType in setOf(EConfigType.VLESS, EConfigType.VMESS, EConfigType.TROJAN,
            EConfigType.SHADOWSOCKS, EConfigType.SOCKS, EConfigType.HTTP) &&
            profile.network.orEmpty().lowercase() !in udpTransports &&
            profile.alpn?.split(',')?.any { it.trim().startsWith("h3") } != true

    fun applyFragment(profile: ProfileItem, outbound: V2rayConfig.OutboundBean) {
        if (profile.manualMode != ManualConfigMode.FRAGMENT) return
        require(supportsFragment(profile)) { "Fragment requires a TCP-based transport" }
        val stream = requireNotNull(outbound.streamSettings) { "Fragment requires stream settings" }
        val masks = stream.finalmask?.let { JsonUtil.parseString(JsonUtil.toJson(it))?.asJsonObject }
            ?: JsonObject()
        val tcp = JsonArray()
        tcp.add(JsonObject().apply {
            addProperty("type", "fragment")
            add("settings", JsonObject().apply {
                addProperty("packets", if (profile.security == AppConfig.TLS) "tlshello" else "1-3")
                addProperty("length", "50-100")
                addProperty("delay", "10-20")
            })
        })
        masks.getAsJsonArray("tcp")?.forEach { mask ->
            if (mask.asJsonObject.get("type")?.asString != "fragment") tcp.add(mask)
        }
        masks.add("tcp", tcp)
        stream.finalmask = masks
    }

    /** Use Google's encrypted resolver for app DNS, with no domestic/plain-DNS fallback. */
    fun applyGoogleDns(config: V2rayConfig) {
        val hosts = config.dns?.hosts.orEmpty().toMutableMap()
        hosts[AppConfig.DNS_GOOGLE_DOMAIN] = AppConfig.DNS_GOOGLE_ADDRESSES
        config.dns = V2rayConfig.DnsBean(
            servers = arrayListOf(GOOGLE_DOH_URL), hosts = hosts, tag = AppConfig.TAG_DNS,
        )
        config.fakedns = null
        config.inbounds.forEach { it.sniffing?.destOverride?.remove("fakedns") }
        if (config.outbounds.none { it.tag == "dns-out" }) {
            config.outbounds.add(V2rayConfig.OutboundBean(
                protocol = "dns", tag = "dns-out", settings = null, streamSettings = null, mux = null,
            ))
        }
        // DNS requests use the proxy; the proxy's own endpoint bootstrap remains unchanged.
        config.routing.rules.add(0, V2rayConfig.RoutingBean.RulesBean(
            inboundTag = arrayListOf(AppConfig.TAG_DNS), outboundTag = AppConfig.TAG_PROXY,
        ))
        config.routing.rules.add(0, V2rayConfig.RoutingBean.RulesBean(
            inboundTag = arrayListOf("socks", "tun"), port = "53", outboundTag = "dns-out",
        ))
    }
}
