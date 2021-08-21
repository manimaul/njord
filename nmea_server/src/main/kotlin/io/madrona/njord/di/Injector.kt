package io.madrona.njord.di

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.willkamp.vial.api.ServerInitializer
import com.willkamp.vial.api.VialConfig
import com.willkamp.vial.api.VialServer
import dagger.Component
import dagger.Module
import dagger.Provides
import io.madrona.njord.*
import io.madrona.njord.ServerApp
import javax.inject.Named
import javax.inject.Singleton

internal val injector: AppComponent = DaggerAppComponent.builder().build()

@Singleton
@Component(modules = [ServerAppModule::class])
internal interface AppComponent {

    fun inject(nmeaSource: NmeaSerialSource)
    fun inject(nmeaSource: NmeaTcpSource)
    fun inject(serverApp: ServerApp)

    @Component.Builder
    interface Builder {
        fun build(): AppComponent
    }
}

@Module
internal class ServerAppModule {
    @Provides
    @Singleton
    @Named("root")
    fun provideConfig(): Config {
        return ConfigFactory.load()
    }

    @Provides
    @Singleton
    @Named("njord")
    fun provideNjordConfig(@Named("root") config: Config): Config {
        return config.getConfig("njord")
    }

    @Provides
    fun provideServer(channelInitializer: NjordChannelInitializer) : ServerInitializer {
        return VialServer.customServer(channelInitializer)
    }

    @Provides
    fun provideVialConfig() : VialConfig {
        return VialConfig()
    }
}