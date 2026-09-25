package opal.dev.wynnoverhaul.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Path
import java.util.UUID

class DiscordIpcConnection private constructor(private val transport: Transport) {
    fun sendActivity(pid: Int, activity: JsonObject) {
        val args = JsonObject()
        args.addProperty("pid", pid)
        args.add("activity", activity)
        val payload = JsonObject()
        payload.addProperty("cmd", "SET_ACTIVITY")
        payload.add("args", args)
        payload.addProperty("nonce", UUID.randomUUID().toString())
        writeFrame(OP_FRAME, payload.toString())
        readResponse()
    }

    private fun readResponse() {
        repeat(MAX_RESPONSE_FRAMES) {
            val (opcode, payload) = readFrame()
            when (opcode) {
                OP_PING -> writeFrame(OP_PONG, payload)
                OP_CLOSE -> throw IOException("Discord closed the connection")
                else -> return
            }
        }
    }

    fun close() {
        try {
            transport.close()
        } catch (_: Exception) {
        }
    }

    private fun writeFrame(opcode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(opcode)
        header.putInt(bytes.size)
        transport.write(header.array())
        transport.write(bytes)
    }

    private fun readFrame(): Pair<Int, String> {
        val header = ByteBuffer.wrap(transport.readFully(8)).order(ByteOrder.LITTLE_ENDIAN)
        val opcode = header.int
        val length = header.int
        val payload = if (length > 0) transport.readFully(length) else ByteArray(0)
        return opcode to String(payload, Charsets.UTF_8)
    }

    companion object {
        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1
        private const val OP_CLOSE = 2
        private const val OP_PING = 3
        private const val OP_PONG = 4
        private const val MAX_RESPONSE_FRAMES = 4

        fun connect(applicationId: Long): DiscordIpcConnection? {
            for (index in 0 until 10) {
                var transport: Transport? = null
                try {
                    transport = openTransport(index) ?: continue
                    val conn = DiscordIpcConnection(transport)
                    conn.writeFrame(OP_HANDSHAKE, "{\"v\":1,\"client_id\":\"$applicationId\"}")
                    val (opcode, json) = conn.readFrame()
                    if (opcode != OP_FRAME) {
                        transport.close()
                        continue
                    }
                    val evt = runCatching { JsonParser.parseString(json).asJsonObject.get("evt")?.asString }.getOrNull()
                    if (evt == "ERROR") {
                        transport.close()
                        continue
                    }
                    return conn
                } catch (_: Exception) {
                    try {
                        transport?.close()
                    } catch (_: Exception) {
                    }
                }
            }
            return null
        }

        private fun openTransport(index: Int): Transport? =
            if (System.getProperty("os.name").lowercase().contains("win")) {
                WindowsPipeTransport.open(index)
            } else {
                UnixSocketTransport.open(index)
            }
    }

    private interface Transport {
        fun write(bytes: ByteArray)
        fun readFully(n: Int): ByteArray
        fun close()
    }

    private class WindowsPipeTransport(private val file: RandomAccessFile) : Transport {
        override fun write(bytes: ByteArray) = file.write(bytes)

        override fun readFully(n: Int): ByteArray {
            val buf = ByteArray(n)
            file.readFully(buf)
            return buf
        }

        override fun close() = file.close()

        companion object {
            fun open(index: Int): Transport? {
                val path = "\\\\.\\pipe\\discord-ipc-$index"
                return try {
                    WindowsPipeTransport(RandomAccessFile(path, "rw"))
                } catch (_: IOException) {
                    null
                }
            }
        }
    }

    private class UnixSocketTransport(private val channel: SocketChannel) : Transport {
        override fun write(bytes: ByteArray) {
            channel.write(ByteBuffer.wrap(bytes))
        }

        override fun readFully(n: Int): ByteArray {
            val buf = ByteBuffer.allocate(n)
            while (buf.hasRemaining()) {
                if (channel.read(buf) < 0) throw IOException("Discord IPC socket closed")
            }
            return buf.array()
        }

        override fun close() = channel.close()

        companion object {
            private val base: String =
                System.getenv("XDG_RUNTIME_DIR")
                    ?: System.getenv("TMPDIR")
                    ?: System.getenv("TMP")
                    ?: System.getenv("TEMP")
                    ?: "/tmp"

            private val subdirs = listOf("", "app/com.discordapp.Discord", "snap.discord")

            fun open(index: Int): Transport? {
                for (subdir in subdirs) {
                    val path = Path.of(base, subdir, "discord-ipc-$index")
                    try {
                        val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
                        try {
                            channel.connect(UnixDomainSocketAddress.of(path))
                        } catch (e: IOException) {
                            channel.close()
                            continue
                        }
                        return UnixSocketTransport(channel)
                    } catch (_: IOException) {
                    }
                }
                return null
            }
        }
    }
}
