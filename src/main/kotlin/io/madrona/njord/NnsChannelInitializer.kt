package io.madrona.njord

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import javax.inject.Inject
import javax.inject.Provider

class NnsChannelInitializer @Inject constructor(
        private val channelHandlerProvider: Provider<NssChannelHandler>
) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(channelHandlerProvider.get())
    }
}
