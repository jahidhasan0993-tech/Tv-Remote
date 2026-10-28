package com.tvremote.free.adb

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

sealed class AdbEvent {
    object Connected : AdbEvent()
    data class AuthRequired(val waitingForUserOnTv: Boolean) : AdbEvent()
    data class Error(val message: String) : AdbEvent()
    object Disconnected : AdbEvent()
}

/**
 * One ADB connection to a single device (identified by host:port, usually
 * <tv-ip>:5555). Call [connect] once, then [runShellCommand] for every
 * button press — each call opens its own multiplexed stream on the same
 * TCP connection, exactly like the real `adb shell <cmd>`.
 */
class AdbConnection(
    private val host: String,
    private val port: Int,
    private val context: Context,
    private val onEvent: (AdbEvent) -> Unit
) {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val crypto = AdbCrypto.loadOrGenerate(context)

    private val nextLocalId = AtomicInteger(1)
    private val streamQueues = ConcurrentHashMap<Int, LinkedBlockingQueue<AdbProtocol.Message>>()

    @Volatile private var readerThread: Thread? = null
    @Volatile private var authAttempts = 0
    @Volatile var isReady = false
        private set

    private val writeLock = Any()

    fun connect() {
        Thread {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                socket = s
                input = DataInputStream(s.getInputStream())
                output = DataOutputStream(s.getOutputStream())

                sendMessage(AdbProtocol.A_CNXN, AdbProtocol.A_VERSION, AdbProtocol.MAX_PAYLOAD, AdbProtocol.cnxnPayload())

                startReaderLoop()
            } catch (e: Exception) {
                onEvent(AdbEvent.Error("সংযোগ ব্যর্থ: ${e.message}"))
            }
        }.start()
    }

    fun disconnect() {
        isReady = false
        try { socket?.close() } catch (_: Exception) {}
        readerThread?.interrupt()
    }

    /** Fire-and-forget: runs `input keyevent <code>` or any shell command. */
    fun runShellCommand(command: String) {
        if (!isReady) {
            onEvent(AdbEvent.Error("এখনো TV-র সাথে সংযুক্ত নয়"))
            return
        }
        Thread {
            try {
                val localId = nextLocalId.incrementAndGet()
                val queue = LinkedBlockingQueue<AdbProtocol.Message>()
                streamQueues[localId] = queue

                sendMessage(AdbProtocol.A_OPEN, localId, 0, AdbProtocol.shellOpenPayload(command))

                // Wait for OKAY (stream accepted), then CLSE (command finished).
                // A short timeout keeps one failed command from hanging the UI.
                var remoteId = -1
                while (true) {
                    val msg = queue.poll(4, java.util.concurrent.TimeUnit.SECONDS) ?: break
                    if (msg.command == AdbProtocol.A_OKAY) remoteId = msg.arg0
                    if (msg.command == AdbProtocol.A_CLSE) break
                }
                if (remoteId != -1) {
                    sendMessage(AdbProtocol.A_CLSE, localId, remoteId)
                }
                streamQueues.remove(localId)
            } catch (e: Exception) {
                onEvent(AdbEvent.Error("কমান্ড ব্যর্থ: ${e.message}"))
            }
        }.start()
    }

    private fun sendMessage(command: Int, arg0: Int, arg1: Int, payload: ByteArray = ByteArray(0)) {
        synchronized(writeLock) {
            output?.write(AdbProtocol.generateMessage(command, arg0, arg1, payload))
            output?.flush()
        }
    }

    private fun startReaderLoop() {
        readerThread = Thread {
            try {
                while (socket?.isClosed == false) {
                    val header = ByteArray(24)
                    input!!.readFully(header)
                    val (command, arg0, arg1, length, _) = AdbProtocol.parseHeader(header).toList()

                    val payload = if (length > 0) ByteArray(length).also { input!!.readFully(it) } else ByteArray(0)
                    handleIncoming(AdbProtocol.Message(command, arg0, arg1, payload))
                }
            } catch (e: Exception) {
                if (isReady) onEvent(AdbEvent.Error("সংযোগ বিচ্ছিন্ন: ${e.message}"))
            } finally {
                isReady = false
                onEvent(AdbEvent.Disconnected)
            }
        }
        readerThread!!.isDaemon = true
        readerThread!!.start()
    }

    private fun handleIncoming(msg: AdbProtocol.Message) {
        when (msg.command) {
            AdbProtocol.A_CNXN -> {
                // Device accepted us without auth (already paired/trusted key).
                isReady = true
                onEvent(AdbEvent.Connected)
            }
            AdbProtocol.A_AUTH -> {
                if (msg.arg0 == AdbProtocol.AUTH_TYPE_TOKEN) {
                    authAttempts++
                    if (authAttempts == 1) {
                        // First try: sign the token with our existing private key.
                        val signature = crypto.signToken(msg.payload)
                        sendMessage(AdbProtocol.A_AUTH, AdbProtocol.AUTH_TYPE_SIGNATURE, 0, signature)
                    } else {
                        // Device didn't recognize our key -> send the public key.
                        // This is what triggers the "Allow debugging?" prompt on the TV.
                        onEvent(AdbEvent.AuthRequired(waitingForUserOnTv = true))
                        sendMessage(AdbProtocol.A_AUTH, AdbProtocol.AUTH_TYPE_RSA_PUBLIC, 0, crypto.getAdbPublicKeyPayload())
                    }
                }
            }
            AdbProtocol.A_OKAY, AdbProtocol.A_WRTE, AdbProtocol.A_CLSE -> {
                if (msg.command == AdbProtocol.A_WRTE) {
                    // ack the write so the device's stream doesn't stall
                    sendMessage(AdbProtocol.A_OKAY, msg.arg1, msg.arg0)
                }
                streamQueues[msg.arg1]?.offer(msg)
            }
        }
    }
}
