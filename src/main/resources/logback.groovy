import ch.qos.logback.classic.filter.ThresholdFilter

appender("CONSOLE", ConsoleAppender) {
    withJansi = true

    filter(ThresholdFilter) {
        level = DEBUG
    }
    encoder(PatternLayoutEncoder) {
        pattern = "%X{appName} %X{instanceId} %-4relative [%thread] %-5level %logger{30} - %msg%n"
        outputPatternAsHeader = false
    }
}

logger("io.netty.util.internal.NativeLibraryLoader", ERROR)
logger("io.netty.util.internal.PlatformDependent0", OFF)
logger("io.netty.handler.ssl.CipherSuiteConverter", OFF)

root(toLevel(System.getenv("LOGLEVEL"), INFO), ["CONSOLE"])
