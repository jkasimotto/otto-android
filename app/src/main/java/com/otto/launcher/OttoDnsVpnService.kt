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
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnLoopStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        vpnLoopStarted = false
        vpnInterface?.close()
        super.onRevoke()
    }

    private suspend fun runVpnLoop() {
        while (currentCoroutineContext().isActive) {
            val tunnel = establishDnsTunnel() ?: break
            vpnInterface = tunnel
            try {
                processPackets(tunnel)
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
            .setMtu(VPN_MTU)
            .addAddress(VPN_INTERFACE_ADDRESS, 24)
            .addDnsServer(VPN_DNS_SERVER)
            .addRoute(VPN_DNS_SERVER, 32)
            .establish()
    }

    private suspend fun processPackets(tunnel: ParcelFileDescriptor) {
        val input = FileInputStream(tunnel.fileDescriptor)
        val output = FileOutputStream(tunnel.fileDescriptor)
        val packetBuffer = ByteArray(MAX_PACKET_SIZE)

        while (currentCoroutineContext().isActive) {
            val packetLength = withContext(Dispatchers.IO) { input.read(packetBuffer) }
            if (packetLength <= 0) break

            val response = OttoDnsPacketProcessor.processPacket(
                packet = packetBuffer,
                length = packetLength,
                isBlockedDomain = { domain -> OttoWebsitePolicy.isBlockedDomain(domain) },
                forwardQuery = { query -> resolveDnsQuery(query) }
            )

            if (response != null) {
                withContext(Dispatchers.IO) {
                    output.write(response)
                    output.flush()
                }
            }
        }
    }

    private suspend fun resolveDnsQuery(query: ByteArray): ByteArray? {
        val upstreamServers = upstreamDnsServers()
        if (upstreamServers.isEmpty()) return null

        for (server in upstreamServers) {
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
            }.getOrNull()

            if (response != null) return response
        }

        return null
    }

    private fun upstreamDnsServers(): List<InetAddress> {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val transportNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@firstOrNull false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        val systemServers = transportNetwork?.let { network ->
            connectivityManager.getLinkProperties(network)?.dnsServers.orEmpty()
        }.orEmpty()

        val ipv4Servers = systemServers.filterIsInstance<Inet4Address>()
        if (ipv4Servers.isNotEmpty()) return ipv4Servers

        return FALLBACK_DNS_SERVERS.mapNotNull { host ->
            runCatching { InetAddress.getByName(host) as? Inet4Address }.getOrNull()
        }
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
        private const val VPN_CHANNEL_ID = "otto_vpn"
        private const val VPN_NOTIFICATION_ID = 2001
        private const val VPN_INTERFACE_ADDRESS = "10.77.0.1"
        private const val VPN_DNS_SERVER = "10.77.0.2"
        private const val VPN_MTU = 1500
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 2_000
        private const val MAX_PACKET_SIZE = 32_767
        private const val MAX_DNS_MESSAGE_SIZE = 4_096
        private val FALLBACK_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")
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
        "ibytedtos.com"
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
