package com.otto.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.math.min

class OttoDnsVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnLoopStarted = false
    private var cachedUpstreamServers: List<InetAddress>? = null
    private var cachedUpstreamNetwork: Network? = null
    private var upstreamServersCacheExpiresAtElapsedRealtime = 0L
    private var lastLoggedUpstreamSummary: String? = null
    private var lastDnsFailureLogElapsedRealtime = 0L
    private var lastUpstreamTimeoutLogElapsedRealtime = 0L
    private var hasLoggedTunnelReady = false
    private var tunnelRestartWindowStartedAtElapsedRealtime = 0L
    private var tunnelRestartCount = 0

    override fun onCreate() {
        super.onCreate()
        serviceActive = true
        OttoDiagnostics.info(
            applicationContext,
            "DnsVpn",
            "VPN service created; ${OttoDiagnostics.processMarker(applicationContext)}"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceActive = true
        OttoDiagnostics.info(
            applicationContext,
            "DnsVpn",
            "VPN service start requested; startId=$startId flags=$flags stickyRestart=${intent == null}; " +
                OttoDiagnostics.processMarker(applicationContext)
        )
        startForegroundNotification()
        if (!vpnLoopStarted) {
            vpnLoopStarted = true
            serviceScope.launch {
                runVpnLoop()
            }
        }
        return Service.START_STICKY
    }

    override fun onDestroy() {
        vpnLoopStarted = false
        vpnInterface?.close()
        serviceActive = false
        serviceScope.cancel()
        hasLoggedTunnelReady = false
        OttoDiagnostics.info(applicationContext, "DnsVpn", "VPN service destroyed.")
        super.onDestroy()
    }

    override fun onRevoke() {
        vpnLoopStarted = false
        vpnInterface?.close()
        serviceActive = false
        hasLoggedTunnelReady = false
        OttoDiagnostics.warn(applicationContext, "DnsVpn", "VPN service revoked by Android.")
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        OttoDiagnostics.warn(applicationContext, "DnsVpn", "VPN service task removed.")
        super.onTaskRemoved(rootIntent)
    }

    private suspend fun runVpnLoop() {
        while (currentCoroutineContext().isActive) {
            val tunnel = establishDnsTunnel()
            if (tunnel == null) {
                OttoDiagnostics.error(applicationContext, "DnsVpn", "Unable to establish DNS tunnel.")
                break
            }
            if (!hasLoggedTunnelReady) {
                OttoDiagnostics.info(applicationContext, "DnsVpn", "DNS tunnel established.")
                hasLoggedTunnelReady = true
            }
            val establishedAt = SystemClock.elapsedRealtime()
            vpnInterface = tunnel
            try {
                val exitReason = processPackets(tunnel)
                handleTunnelExit(establishedAt, exitReason)
            } finally {
                runCatching { tunnel.close() }
                vpnInterface = null
            }
        }
        stopSelf()
    }

    private fun establishDnsTunnel(): ParcelFileDescriptor? {
        return Builder()
            .setSession("Otto Website Shield")
            .setBlocking(true)
            .setMtu(VPN_MTU)
            .addAddress(VPN_INTERFACE_ADDRESS, 24)
            .addDnsServer(VPN_DNS_SERVER)
            .addRoute(VPN_DNS_SERVER, 32)
            .establish()
    }

    private suspend fun processPackets(tunnel: ParcelFileDescriptor): String {
        val input = FileInputStream(tunnel.fileDescriptor)
        val output = FileOutputStream(tunnel.fileDescriptor)
        val packetBuffer = ByteArray(MAX_PACKET_SIZE)

        while (currentCoroutineContext().isActive) {
            val packetLength = runCatching { input.read(packetBuffer) }
                .getOrElse { error -> return "input read failed (${error.javaClass.simpleName})" }
            if (packetLength <= 0) return "tunnel input closed"

            val response = OttoDnsPacketProcessor.processPacket(
                packet = packetBuffer,
                length = packetLength,
                isBlockedDomain = { domain -> OttoWebsitePolicy.isBlockedDomain(domain) },
                forwardQuery = { query -> resolveDnsQuery(query) }
            )

            if (response != null) {
                val writeSucceeded = runCatching {
                    output.write(response)
                }.isSuccess
                if (!writeSucceeded) {
                    return "tunnel output write failed"
                }
            }
        }
        return "packet loop cancelled"
    }

    private suspend fun handleTunnelExit(establishedAt: Long, exitReason: String) {
        val now = SystemClock.elapsedRealtime()
        val runtimeMs = now - establishedAt
        if (runtimeMs >= TUNNEL_FLAP_WINDOW_MS) {
            tunnelRestartCount = 0
            tunnelRestartWindowStartedAtElapsedRealtime = 0L
            return
        }

        if (tunnelRestartWindowStartedAtElapsedRealtime == 0L ||
            now - tunnelRestartWindowStartedAtElapsedRealtime > TUNNEL_FLAP_WINDOW_MS
        ) {
            tunnelRestartWindowStartedAtElapsedRealtime = now
            tunnelRestartCount = 1
        } else {
            tunnelRestartCount += 1
        }

        if (tunnelRestartCount == TUNNEL_FLAP_LOG_THRESHOLD ||
            tunnelRestartCount % TUNNEL_FLAP_LOG_INTERVAL == 0
        ) {
            OttoDiagnostics.warn(
                applicationContext,
                "DnsVpn",
                "DNS tunnel is flapping: $tunnelRestartCount restarts in " +
                    "${now - tunnelRestartWindowStartedAtElapsedRealtime}ms; last exit=$exitReason"
            )
        }

        delay((tunnelRestartCount * TUNNEL_RESTART_BACKOFF_STEP_MS).coerceAtMost(TUNNEL_RESTART_BACKOFF_MAX_MS))
    }

    private suspend fun resolveDnsQuery(query: ByteArray): ByteArray? {
        val upstreamServers = upstreamDnsServers()
        if (upstreamServers.isEmpty()) {
            logDnsFailure("No upstream DNS servers available.")
            return null
        }

        for (server in upstreamServers) {
            val queryStartedAt = SystemClock.elapsedRealtime()
            val response = runCatching {
                DatagramSocket().use { socket ->
                    if (!protect(socket)) return@use null
                    socket.soTimeout = DNS_TIMEOUT_MS
                    socket.connect(server, DNS_PORT)
                    socket.send(DatagramPacket(query, query.size))

                    val responseBuffer = ByteArray(MAX_DNS_MESSAGE_SIZE)
                    val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                    socket.receive(responsePacket)
                    responsePacket.data.copyOf(responsePacket.length)
                }
            }.onFailure { error ->
                logUpstreamTimeout(server, error, SystemClock.elapsedRealtime() - queryStartedAt)
            }.getOrNull()

            if (response != null) return response
        }

        logDnsFailure(
            "DNS query failed against upstream servers: ${upstreamServers.joinToString { it.hostAddress.orEmpty() }}"
        )
        return null
    }

    private fun upstreamDnsServers(): List<InetAddress> {
        val preferredNetwork = runCatching { preferredTransportNetwork() }
            .onFailure { error ->
                OttoDiagnostics.warn(
                    applicationContext,
                    "DnsVpn",
                    "Unable to inspect active transport network; falling back to public DNS (${error.javaClass.simpleName})."
                )
            }
            .getOrNull()
        val now = SystemClock.elapsedRealtime()
        val cached = cachedUpstreamServers
        if (cached != null &&
            preferredNetwork == cachedUpstreamNetwork &&
            now < upstreamServersCacheExpiresAtElapsedRealtime
        ) {
            return cached
        }

        val systemServers = preferredNetwork?.let { network ->
            runCatching { connectivityManager.getLinkProperties(network)?.dnsServers.orEmpty() }
                .getOrDefault(emptyList())
        }.orEmpty()

        val resolvedServers = if (systemServers.isNotEmpty()) {
            prioritizeDnsServers(systemServers.distinct())
        } else {
            FALLBACK_DNS_SERVER_ADDRESSES
        }
        val upstreamSummary = buildString {
            append(describeNetwork(preferredNetwork))
            append(" dns=")
            append(resolvedServers.joinToString { it.hostAddress.orEmpty() })
        }
        if (upstreamSummary != lastLoggedUpstreamSummary) {
            OttoDiagnostics.info(applicationContext, "DnsVpn", "Selected upstream transport: $upstreamSummary")
            lastLoggedUpstreamSummary = upstreamSummary
        }
        cachedUpstreamNetwork = preferredNetwork
        cachedUpstreamServers = resolvedServers
        upstreamServersCacheExpiresAtElapsedRealtime = now + UPSTREAM_DNS_CACHE_TTL_MS
        return resolvedServers
    }

    private fun prioritizeDnsServers(servers: List<InetAddress>): List<InetAddress> {
        return servers.sortedWith(
            compareBy<InetAddress> { if (it is Inet4Address) 0 else 1 }
                .thenBy { servers.indexOf(it) }
        )
    }

    private fun preferredTransportNetwork(): Network? {
        val activeNetwork = connectivityManager.activeNetwork
        val candidates = connectivityManager.allNetworks.mapNotNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return@mapNotNull null
            }
            network to capabilities
        }
        val activeCandidate = candidates.firstOrNull { it.first == activeNetwork }
        if (activeCandidate?.second?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
            return activeCandidate.first
        }

        return candidates.firstOrNull { (_, capabilities) ->
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }?.first ?: activeCandidate?.first ?: candidates.firstOrNull()?.first
    }

    private fun describeNetwork(network: Network?): String {
        if (network == null) return "none"
        val capabilities = runCatching { connectivityManager.getNetworkCapabilities(network) }
            .getOrNull()
        val parts = mutableListOf<String>()
        parts += "id=$network"
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) parts += "wifi"
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) parts += "cellular"
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) parts += "ethernet"
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) parts += "validated"
        return parts.joinToString(separator = ",")
    }

    private fun logDnsFailure(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDnsFailureLogElapsedRealtime < DNS_FAILURE_LOG_INTERVAL_MS) return
        lastDnsFailureLogElapsedRealtime = now
        OttoDiagnostics.warn(applicationContext, "DnsVpn", message)
    }

    private fun logUpstreamTimeout(server: InetAddress, error: Throwable, elapsedMs: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastUpstreamTimeoutLogElapsedRealtime < UPSTREAM_TIMEOUT_LOG_INTERVAL_MS) return
        lastUpstreamTimeoutLogElapsedRealtime = now
        OttoDiagnostics.warn(
            applicationContext,
            "DnsVpn",
            "Upstream DNS server ${server.hostAddress.orEmpty()} failed after ${elapsedMs}ms " +
                "(${error.javaClass.simpleName})."
        )
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                VPN_CHANNEL_ID,
                "Otto VPN",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val notification = Notification.Builder(this, VPN_CHANNEL_ID)
            .setContentTitle("Otto website shield")
            .setContentText("Blocking configured websites across the device.")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                VPN_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(VPN_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        @Volatile
        private var serviceActive = false

        private const val VPN_CHANNEL_ID = "otto_vpn"
        private const val VPN_NOTIFICATION_ID = 2001
        private const val VPN_INTERFACE_ADDRESS = "10.77.0.1"
        private const val VPN_DNS_SERVER = "10.77.0.2"
        private const val VPN_MTU = 1500
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 2_000
        private const val DNS_FAILURE_LOG_INTERVAL_MS = 10_000L
        private const val UPSTREAM_TIMEOUT_LOG_INTERVAL_MS = 30_000L
        private const val UPSTREAM_DNS_CACHE_TTL_MS = 5_000L
        private const val TUNNEL_FLAP_WINDOW_MS = 5_000L
        private const val TUNNEL_FLAP_LOG_THRESHOLD = 3
        private const val TUNNEL_FLAP_LOG_INTERVAL = 10
        private const val TUNNEL_RESTART_BACKOFF_STEP_MS = 250L
        private const val TUNNEL_RESTART_BACKOFF_MAX_MS = 2_000L
        private const val MAX_PACKET_SIZE = 32_767
        private const val MAX_DNS_MESSAGE_SIZE = 4_096
        private val FALLBACK_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")
        private val FALLBACK_DNS_SERVER_ADDRESSES by lazy {
            FALLBACK_DNS_SERVERS.mapNotNull { host ->
                runCatching { InetAddress.getByName(host) as? Inet4Address }.getOrNull()
            }
        }

        fun isActive(): Boolean {
            return serviceActive
        }
    }
}

private object OttoWebsitePolicy {
    private val blockedDomainSuffixes = setOf(
        "reddit.com",
        "redd.it",
        "redditmedia.com",
        "redditstatic.com",
        "tiktok.com",
        "tiktokcdn.com",
        "tiktokv.com",
        "ttwstatic.com",
        "ibyteimg.com",
        "ibytedtos.com",
        "9gag.com",
        "9cache.com"
    )

    fun isBlockedDomain(domain: String): Boolean {
        val normalizedDomain = domain.lowercase().trimEnd('.')
        return blockedDomainSuffixes.any { suffix ->
            normalizedDomain == suffix || normalizedDomain.endsWith(".$suffix")
        }
    }
}

private object OttoDnsPacketProcessor {
    private const val IPV4_HEADER_LENGTH = 20
    private const val UDP_HEADER_LENGTH = 8
    private const val UDP_PROTOCOL = 17

    suspend fun processPacket(
        packet: ByteArray,
        length: Int,
        isBlockedDomain: (String) -> Boolean,
        forwardQuery: suspend (ByteArray) -> ByteArray?
    ): ByteArray? {
        if (length < IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
        if (ipHeaderLength < IPV4_HEADER_LENGTH || length < ipHeaderLength + UDP_HEADER_LENGTH) return null
        if (packet[9].toInt() and 0xFF != UDP_PROTOCOL) return null

        val totalLength = min(readUnsignedShort(packet, 2), length)
        val sourceIp = packet.copyOfRange(12, 16)
        val destinationIp = packet.copyOfRange(16, 20)
        val sourcePort = readUnsignedShort(packet, ipHeaderLength)
        val destinationPort = readUnsignedShort(packet, ipHeaderLength + 2)
        if (destinationPort != 53) return null

        val udpLength = readUnsignedShort(packet, ipHeaderLength + 4)
        val dnsStart = ipHeaderLength + UDP_HEADER_LENGTH
        val dnsLength = min(udpLength - UDP_HEADER_LENGTH, totalLength - dnsStart)
        if (dnsLength <= 0 || dnsStart + dnsLength > length) return null

        val dnsQuery = packet.copyOfRange(dnsStart, dnsStart + dnsLength)
        val domain = parseQuestionDomain(dnsQuery) ?: return null

        val dnsResponse = if (isBlockedDomain(domain)) {
            buildBlockedDnsResponse(dnsQuery)
        } else {
            forwardQuery(dnsQuery)
        } ?: return null

        return buildUdpIpv4Response(
            sourceIp = destinationIp,
            destinationIp = sourceIp,
            sourcePort = destinationPort,
            destinationPort = sourcePort,
            payload = dnsResponse
        )
    }

    private fun buildBlockedDnsResponse(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val response = query.copyOf()
        response[2] = (response[2].toInt() or 0x80).toByte()
        response[3] = ((response[3].toInt() and 0xF0) or 0x80 or 0x03).toByte()
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0
        return response
    }

    private fun parseQuestionDomain(query: ByteArray): String? {
        if (query.size < 12) return null
        if (readUnsignedShort(query, 4) == 0) return null

        var offset = 12
        val labels = mutableListOf<String>()
        while (offset < query.size) {
            val labelLength = query[offset].toInt() and 0xFF
            if (labelLength == 0) {
                return labels.joinToString(".")
            }
            if ((labelLength and 0xC0) != 0) return null
            val labelStart = offset + 1
            val labelEnd = labelStart + labelLength
            if (labelEnd > query.size) return null
            labels += String(query, labelStart, labelLength, Charsets.US_ASCII)
            offset = labelEnd
        }
        return null
    }

    private fun buildUdpIpv4Response(
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + payload.size
        val totalLength = IPV4_HEADER_LENGTH + udpLength
        val buffer = ByteBuffer.allocate(totalLength)

        buffer.put(0x45.toByte())
        buffer.put(0)
        buffer.putShort(totalLength.toShort())
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.put(64.toByte())
        buffer.put(UDP_PROTOCOL.toByte())
        buffer.putShort(0)
        buffer.put(sourceIp)
        buffer.put(destinationIp)
        buffer.putShort(sourcePort.toShort())
        buffer.putShort(destinationPort.toShort())
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0)
        buffer.put(payload)

        val packet = buffer.array()
        val checksum = ipHeaderChecksum(packet, IPV4_HEADER_LENGTH)
        packet[10] = (checksum ushr 8).toByte()
        packet[11] = checksum.toByte()
        return packet
    }

    private fun ipHeaderChecksum(packet: ByteArray, headerLength: Int): Int {
        var sum = 0L
        var offset = 0
        while (offset < headerLength) {
            if (offset != 10) {
                sum += readUnsignedShort(packet, offset).toLong()
            }
            offset += 2
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv().toInt() and 0xFFFF
    }

    private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }
}
