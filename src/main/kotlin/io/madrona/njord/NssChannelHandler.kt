package io.madrona.njord

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class NssChannelHandler @Inject constructor(
        private val nmeaStreams: NmeaStreams
) : ChannelInboundHandlerAdapter() {
    private val log = logger()
    private val compositeDisposable = CompositeDisposable()

    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        log.info("channel active: {}", ctx.channel())
        compositeDisposable.add(
                nmeaStreams
                        .nmeaData()
                        .map {  ctx.alloc().buffer(it.size, it.size).writeBytes(it) }
                        .subscribe { ctx.writeAndFlush(it) })
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        log.info("channel inactive: {}", ctx.channel())
        compositeDisposable.clear()
    }

}