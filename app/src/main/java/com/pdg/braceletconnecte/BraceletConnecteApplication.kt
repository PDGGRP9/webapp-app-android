package com.pdg.braceletconnecte

import android.app.Application
import com.pdg.braceletconnecte.data.api.ApiClientProvider
import com.pdg.braceletconnecte.data.api.AuthTokenHolder
import com.pdg.braceletconnecte.data.auth.AuthRepository
import com.pdg.braceletconnecte.data.bracelet.BraceletRepository
import com.pdg.braceletconnecte.data.local.BraceletDatabase
import com.pdg.braceletconnecte.data.local.MeasurementStore
import com.pdg.braceletconnecte.data.measurements.MeasurementUploader
import com.pdg.braceletconnecte.data.measurements.MeasurementsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Small manual dependency container — the project has no DI framework and doesn't need one yet. */
class BraceletConnecteApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val authTokenHolder = AuthTokenHolder()
    val apiClientProvider by lazy { ApiClientProvider(authTokenHolder) }
    val authRepository by lazy { AuthRepository(this, apiClientProvider, authTokenHolder, applicationScope) }
    val braceletRepository by lazy { BraceletRepository(apiClientProvider, authRepository) }
    val measurementsRepository by lazy { MeasurementsRepository(apiClientProvider, authRepository) }

    // Local measurement database: BLE writes here, the uploader drains it to the
    // backend in the background. The BLE link therefore never waits for the network.
    private val database by lazy { BraceletDatabase.build(this) }
    val measurementStore by lazy { MeasurementStore(database.measurementDao()) }
    val uploader by lazy {
        MeasurementUploader(measurementStore, measurementsRepository, authRepository, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        uploader.start()
    }
}
