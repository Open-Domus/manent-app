// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GatewayModule {
    @Binds
    abstract fun pairingStore(store: SealedPairingStore): PairingStore

    @Binds
    abstract fun availabilityMemory(memory: PrefsAvailabilityMemory): AvailabilityMemory

    companion object {
        @Provides
        @Singleton
        fun connector(): GatewayConnector = GatewayConnector.platform()
    }
}
