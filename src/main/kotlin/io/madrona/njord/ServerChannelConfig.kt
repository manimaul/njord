package io.madrona.njord

import io.netty.channel.EventLoopGroup
import io.netty.channel.ServerChannel
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerChannelConfig @Inject internal constructor(njordConfig: NjordConfig) {
    val eventLoopGroup: EventLoopGroup
    val bossEventLoopGroup: EventLoopGroup
    val channelClass: Class<out ServerChannel?>

    init {
        when {
            Epoll.isAvailable() -> {
                eventLoopGroup = EpollEventLoopGroup(njordConfig.bossThreads)
                bossEventLoopGroup = EpollEventLoopGroup(njordConfig.workerThreads)
                channelClass = EpollServerSocketChannel::class.java
            }
            KQueue.isAvailable() -> {
                eventLoopGroup = KQueueEventLoopGroup(njordConfig.bossThreads)
                bossEventLoopGroup = KQueueEventLoopGroup(njordConfig.workerThreads)
                channelClass = KQueueServerSocketChannel::class.java
            }
            else -> {
                eventLoopGroup = NioEventLoopGroup(njordConfig.bossThreads)
                bossEventLoopGroup = NioEventLoopGroup(njordConfig.workerThreads)
                channelClass = NioServerSocketChannel::class.java
            }
        }
    }
}