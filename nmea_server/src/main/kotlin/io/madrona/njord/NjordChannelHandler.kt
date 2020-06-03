package io.madrona.njord

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NjordChannelHandler @Inject constructor(
        private val nmeaStreams: NmeaStreams,
        private val nmeaChecksum: NmeaChecksum
) : ChannelInboundHandlerAdapter() {
    private val log = logger()
    private val compositeDisposable = CompositeDisposable()

    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        log.info("channel active: {}", ctx.channel())
        nmeaChecksum.createVendorMessage("njord connection acknowledged from ${ctx.channel().remoteAddress()}")?.let {
            writeLine(ctx, it)
        }
        Observable.interval(5, TimeUnit.SECONDS)
        compositeDisposable.addAll(
                nmeaStreams
                        .nmeaData()
                        .subscribe {
                            writeLine(ctx, it)
                        },
                Observable.interval(5, TimeUnit.SECONDS).map {
                    nmeaChecksum.createVendorMessage("njord connection acknowledged from ${ctx.channel().remoteAddress()}") ?: ""
                }.subscribe {
                    writeLine(ctx, it)
                }
        )
    }

    private fun writeLine(ctx: ChannelHandlerContext, line: String) {
        val bytes = line.toByteArray(StandardCharsets.US_ASCII) //nmea 0183
        val buffer = ctx.alloc().buffer(bytes.size, bytes.size).writeBytes(bytes)
        ctx.writeAndFlush(buffer)
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        log.info("channel inactive: {}", ctx.channel())
        compositeDisposable.clear()
    }

}