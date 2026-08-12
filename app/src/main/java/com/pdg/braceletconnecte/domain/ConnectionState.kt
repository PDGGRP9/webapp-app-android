package com.pdg.braceletconnecte.domain

enum class ConnectionState {
    Idle,
    Scanning,
    Connecting,
    Connected,
    Publishing,
    Stopped,
    Error,
}
