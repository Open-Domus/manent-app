// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.feature.omi.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.manentlabs.app.feature.omi.OmiLink
import it.manentlabs.app.feature.omi.OmiPairing
import it.manentlabs.app.feature.omi.OmiPairingStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OmiModule {
    @Provides
    @Singleton
    fun providePairingStore(@ApplicationContext context: Context): OmiPairingStore =
        OmiPairingStore(context.getSharedPreferences(OmiPairingStore.PREFS_NAME, Context.MODE_PRIVATE))

    @Provides
    @Singleton
    fun providePairing(@ApplicationContext context: Context, store: OmiPairingStore): OmiPairing = OmiPairing(context, store)

    @Provides
    @Singleton
    fun provideLink(@ApplicationContext context: Context, store: OmiPairingStore): OmiLink = OmiLink(context, store)
}

// The Manent app binds WearableAudio itself, to whichever wearable is in use.
