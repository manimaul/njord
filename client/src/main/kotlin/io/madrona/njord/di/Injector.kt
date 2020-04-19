package io.madrona.njord.di

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import dagger.Component
import dagger.Module
import dagger.Provides
import io.madrona.njord.NmeaChecksum
import io.madrona.njord.NmeaClient
import javax.inject.Named
import javax.inject.Singleton

internal val injector: AppComponent = DaggerAppComponent.builder().build()

@Singleton
@Component(modules = [AppModule::class])
internal interface AppComponent {
    fun nmeaClient(): NmeaClient
    fun checksum(): NmeaChecksum

    @Component.Builder
    interface Builder {
        fun build(): AppComponent
    }
}

@Module
internal class AppModule {
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