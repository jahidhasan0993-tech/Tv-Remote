package com.tvremote.free.adb

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal implementation of the ADB (Android Debug Bridge) host<->device
 * wire protocol, just enough to open a "shell:" stream and send
 * `input keyevent` / `input text` commands to a device that has
 * ADB-over-network (Wi-Fi debugging) turned on.
 *
 * This is the same open protocol the official `adb` command line tool
 * uses; no bypass of any protection is involved. The very first
 * connection will show a "Allow USB/Wireless debugging?" prompt on the
 * TV screen that the user must accept there, exactly like plugging in
 * `adb` from a PC.
 *
 * Reference: https://android.googlesource.com/platform/system/core/+/main/adb/protocol.txt
 */
object AdbProtocol {

    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257
    const val A_AUTH = 0x48545541

    const val A_VERSION = 0x01000000

    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSA_PUBLIC = 3

    const val MAX_PAYLOAD = 4096

    data class Message(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray
    )

    /** Builds the 24-byte header + payload for one ADB message. */
    fun generateMessage(command: Int, arg0: Int, arg1: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val buffer = ByteBuffer.allocate(24 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        var checksum = 0
        for (b in payload) checksum += (b.toInt() and 0xFF)

        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(payload.size)
        buffer.putInt(checksum)
        buffer.putInt(command.inv())
        buffer.put(payload)
        return buffer.array()
    }

    /** Parses a 24-byte ADB header read from the socket. */
    fun parseHeader(header: ByteArray): IntArray {
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val length = buf.int
        val checksum = buf.int
        // magic (header[20..23]) is ignored on read, matching common client behavior
        return intArrayOf(command, arg0, arg1, length, checksum)
    }

    fun cnxnPayload(systemType: String = "host", serial: String = "tvremote", features: String = "shell_v2,cmd"): ByteArray {
        val out = ByteArrayOutputStream()
        val s = "$systemType::features=$features\u0000"
        out.write(s.toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    /** "shell,raw:<command>\u0000" destination string for an OPEN message. */
    fun shellOpenPayload(command: String): ByteArray {
        return ("shell:$command\u0000").toByteArray(Charsets.UTF_8)
    }
}
