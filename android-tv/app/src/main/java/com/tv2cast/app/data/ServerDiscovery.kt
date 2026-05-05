package com.tv2cast.app.data

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Helper class to discover Tv2Cast server via UDP broadcast
 */
class ServerDiscovery(private val context: Context) {
    private val TAG = "ServerDiscovery"
    private val DISCOVERY_PORT = 3457
    private val DISCOVERY_MSG = "TV2CAST_DISCOVER"
    private val TIMEOUT_MS = 5000

    /**
     * Broadcasts a discovery message and waits for a response from the server.
     * Returns the server's base URL (e.g., http://192.168.1.100:3456) or null if not found.
     */
    suspend fun discoverServer(): String? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null
        
        try {
            // Acquire MulticastLock — some devices need this to receive UDP broadcasts
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("Tv2CastDiscovery").apply {
                setReferenceCounted(true)
                acquire()
            }

            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = TIMEOUT_MS

            val sendData = DISCOVERY_MSG.toByteArray()
            
            // Try specific subnet broadcast first, then global broadcast
            val addresses = mutableListOf<InetAddress>()
            getBroadcastAddress()?.let { addresses.add(it) }
            addresses.add(InetAddress.getByName("255.255.255.255"))

            for (addr in addresses) {
                Log.d(TAG, "Sending broadcast to $addr")
                val sendPacket = DatagramPacket(sendData, sendData.size, addr, DISCOVERY_PORT)
                socket.send(sendPacket)
            }

            // Wait for response
            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            
            socket.receive(receivePacket)
            
            val responseText = String(receivePacket.data, 0, receivePacket.length)
            Log.d(TAG, "Received response: $responseText from ${receivePacket.address}")
            
            val json = JSONObject(responseText)
            val port = json.getInt("port")
            val host = receivePacket.address.hostAddress
            
            return@withContext "http://$host:$port"

        } catch (e: SocketTimeoutException) {
            Log.d(TAG, "Discovery timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Discovery error: ${e.message}", e)
        } finally {
            socket?.close()
            if (multicastLock?.isHeld == true) {
                multicastLock.release()
            }
        }
        return@withContext null
    }

    private fun getBroadcastAddress(): InetAddress? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wifi.dhcpInfo ?: return null
        val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
        val quads = ByteArray(4)
        for (k in 0..3) {
            quads[k] = (broadcast shr (k * 8) and 0xFF).toByte()
        }
        try {
            val addr = InetAddress.getByAddress(quads)
            if (addr.hostAddress == "0.0.0.0") return null
            return addr
        } catch (e: Exception) {
            return null
        }
    }
}
