package io.madrona.njord

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class NmeaServer @Inject constructor(
        private val njordConfig: NjordConfig,
        private val serverChannelConfig: ServerChannelConfig,
        private val channelInitializer: NjordChannelInitializer) : Closeable {
    private val log = logger()
    private var channelFuture: ChannelFuture? = null
    fun listenAndServeBlocking() {
        serve(null)
    }

    fun listenAndServe(): CompletableFuture<NonBlockingResult> {
        val future = CompletableFuture<NonBlockingResult>()
        Thread(Runnable { serve(future) }).start()
        return future
    }

    private fun serve(future: CompletableFuture<NonBlockingResult>?) {
        val bootstrap = ServerBootstrap()
        try {
            val address = InetAddress.getByName("0.0.0.0")
            val socketAddress = InetSocketAddress(address, 10110)
            bootstrap
                    .group(
                            serverChannelConfig.bossEventLoopGroup, serverChannelConfig.eventLoopGroup)
                    .channel(serverChannelConfig.channelClass)
                    .localAddress(socketAddress)
                    .childOption(ChannelOption.TCP_NODELAY, true) // turn off Nagle's Algo
                    .option(
                            ChannelOption.SO_BACKLOG,
                            njordConfig.maxConnBacklog) // max connection queue backlog size
                    .option(
                            ChannelOption.CONNECT_TIMEOUT_MILLIS,
                            njordConfig.connTimeout) // connection timeout
                    .option( // write task queue high and low watermarks
                            ChannelOption.WRITE_BUFFER_WATER_MARK,
                            WriteBufferWaterMark(
                                    njordConfig.writeBufferQueueSizeBytesLow,
                                    njordConfig.writeBufferQueueSizeBytesHigh))
                    .childHandler(channelInitializer)
            channelFuture = bootstrap.bind()
            channelFuture?.let { cf: ChannelFuture ->
                cf.addListener {
                    future?.complete(NonBlockingResult(this, cf.channel()))
                }
            }
            channelFuture?.sync()
            channelFuture?.channel()?.closeFuture()?.sync()
            channelFuture = null
        } catch (e: UnknownHostException) {
            log.error("error", e)
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            log.error("error", e)
            throw RuntimeException(e)
        } finally {
            close()
        }
    }

    override fun close() {
        try {
            if (channelFuture != null) {
                channelFuture!!.channel().close()
            }
            serverChannelConfig.eventLoopGroup.shutdownGracefully().sync()
        } catch (e: InterruptedException) {
            log.error("error closing", e)
            throw RuntimeException(e)
        }
    }

    class NonBlockingResult internal constructor(private val closeable: Closeable, private val channel: Channel) : Closeable {
        private val log = logger()
        override fun close() {
            try {
                closeable.close()
            } catch (e: IOException) {
                log.error("error closing", e)
            }
        }

    }

}