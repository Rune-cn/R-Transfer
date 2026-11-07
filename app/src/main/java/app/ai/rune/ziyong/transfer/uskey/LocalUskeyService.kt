// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.uskey

import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

/**
 * 本机回环 USKey 服务：与外部 `uskey_browser.py --serve` 输出**同一契约**
 * （GET /uskey → `{ok,h38,ts,params,uskey}`），因此 :core 的 `mintFromService`
 * 无需任何改动——内置服务与电脑版服务可无缝互换。
 *
 * 极简实现：只绑定 127.0.0.1 随机端口，单线程串行处理（铸造本身有锁，
 * 并发请求排队即可）。只响应 `GET /uskey`，其它路径回 404。
 *
 * 为什么不用 nanohttpd：:core 依赖 nanohttpd 但不透出给 :app；
 * 为这一个端点引依赖不值，ServerSocket 足以（且不用为 URL 解码引入解析库）。
 */
class LocalUskeyService(
    private val minter: WebViewUskeyMinter,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "uskey-local-service").apply { isDaemon = true }
    }

    @Volatile
    private var server: ServerSocket? = null

    @Volatile
    private var port: Int = 0

    private val running = AtomicBoolean(false)

    /** 当前监听端口（0 = 未启动）。 */
    val boundPort: Int get() = port

    /** 服务是否就绪。 */
    val isRunning: Boolean get() = running.get()

    /** 完整服务地址（供账号配置使用）。 */
    fun serviceUrl(): String = "http://127.0.0.1:$port/uskey"

    /** 启动服务（幂等）：绑定随机端口并开始 accept 循环。 */
    fun start(cookie: () -> String) {
        if (running.get()) return
        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", 0))
            }
        } catch (e: Exception) {
            Timber.e("USKey 本地服务启动失败：${e.message}")
            return
        }
        server = socket
        port = socket.localPort
        running.set(true)
        executor.execute { acceptLoop(socket, cookie) }
        Timber.i("USKey 本地服务已启动：127.0.0.1:%d/uskey", port)
    }

    /** 停止服务并释放端口（进程退出/彻底关闭时调用）。 */
    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        port = 0
    }

    private fun acceptLoop(socket: ServerSocket, cookie: () -> String) {
        while (running.get() && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                break // 服务已关闭
            }
            try {
                handle(client, cookie)
            } catch (e: Exception) {
                Timber.w("USKey 本地服务请求异常：${e.message}")
            } finally {
                runCatching { client.close() }
            }
        }
    }

    private fun handle(client: Socket, cookie: () -> String) {
        client.soTimeout = 35_000
        val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
        val requestLine = reader.readLine() ?: return
        val path = requestLine.split(" ").getOrNull(1).orEmpty()
        // 读到请求头结束（消费剩余行），避免「连接复用」时残留干扰——我们收尾就关闭，简单起见只读到空行
        var line = reader.readLine()
        while (line != null && line.isNotEmpty()) {
            line = reader.readLine()
        }

        val out = BufferedOutputStream(client.getOutputStream())
        if (path.startsWith("/uskey")) {
            val result = minter.mint(cookie())
            val ok = if (result.ok) {
                """{"ok":true,"h38":"${result.h38}","ts":${result.timestamp},"params":"${escape(result.params)}","uskey":"${escape(result.uskey)}"}"""
            } else {
                """{"ok":false,"reason":"${escape(result.reason)}"}"""
            }
            writeJson(out, ok)
        } else {
            writeResponse(out, 404, "{\"ok\":false,\"reason\":\"not-found\"}")
        }
        out.flush()
    }

    private fun writeJson(out: BufferedOutputStream, body: String) {
        writeResponse(out, 200, body)
    }

    private fun writeResponse(out: BufferedOutputStream, status: Int, body: String) {
        val reason = if (status == 200) "OK" else "Not Found"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)
        out.write(head)
        out.write(bytes)
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}