package dev.aigw.core.util

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * 端口可用性探测。
 *
 * 为什么需要它：`EADDRINUSE` 是用户最常遇到的启动失败原因（上一次的网关进程还没退干净、
 * 或别的应用占了这个端口），而原始异常只有一行 `bind failed`，用户无从判断该怎么办。
 * 启动前先探测，就能给出「端口被占用，已改用 XXXX」这种可执行的信息。
 */
object PortProbe {

    /**
     * 探测端口是否可以绑定。
     *
     * @param host `null` 表示绑定所有网卡（与 [dev.aigw.core.gateway.GatewayHttpServer] 的约定一致）；
     *             否则只探测该地址。
     */
    fun isAvailable(port: Int, host: String? = "127.0.0.1"): Boolean {
        return try {
            // 必须 setReuseAddress(false)：否则 TIME_WAIT 状态的端口会被误判为可用，
            // 真正 bind 时仍然失败。
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                val address = if (host == null) {
                    InetSocketAddress(port)
                } else {
                    InetSocketAddress(InetAddress.getByName(host), port)
                }
                socket.bind(address)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 从 [preferred] 开始找一个可用端口。
     *
     * 上限 20 次：再往后说明不是「偶发占用」而是环境问题，
     * 继续扫只会让用户等更久。
     *
     * @return 可用端口；找不到返回 null（调用方据此给出明确失败提示）。
     */
    fun findAvailable(preferred: Int, host: String? = "127.0.0.1", maxTries: Int = 20): Int? {
        for (offset in 0 until maxTries) {
            val candidate = preferred + offset
            // 端口上限是 65535，越过就回绕到 1024 继续找
            val port = if (candidate <= 65_535) candidate else 1024 + (candidate - 65_536)
            if (port in 1024..65_535 && isAvailable(port, host)) return port
        }
        return null
    }
}
