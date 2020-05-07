package io.madrona.njord.di

import dagger.Component
import dagger.Module
import io.madrona.njord.NmeaChecksum
import io.madrona.njord.NmeaClient
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
internal class AppModule