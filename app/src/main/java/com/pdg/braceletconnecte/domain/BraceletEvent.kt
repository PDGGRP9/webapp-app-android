package com.pdg.braceletconnecte.domain

sealed interface BraceletEvent {
    data class StateChanged(val state: ConnectionState) : BraceletEvent
    data class MeasurementReceived(val measurement: BiometricMeasurement) : BraceletEvent
    data class Error(val message: String, val throwable: Throwable? = null) : BraceletEvent
}
