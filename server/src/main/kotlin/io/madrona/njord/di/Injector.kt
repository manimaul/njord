package io.madrona.njord.di

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import dagger.Component
import dagger.Module
import dagger.Provides
import io.madrona.njord.NmeaServer
import io.madrona.njord.NmeaSerialSource
import io.madrona.njord.NmeaTcpSource
import javax.inject.Named
import javax.inject.Singleton

internal val injector: AppComponent = DaggerAppComponent.builder().build()

@Singleton
@Component(modules = [ServerAppModule::class])
internal interface AppComponent {

    fun inject(nmeaSource: NmeaSerialSource)
    fun inject(nmeaSource: NmeaTcpSource)

    val nmeaServer: NmeaServer

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
}