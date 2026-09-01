package com.pdg.braceletconnecte.domain

enum class ConnectionState {
    Idle,
    Scanning,
    Connecting,
    Connected,
    /** Catching up on the bracelet backlog, packet by packet, with ACK. */
    Syncing,
    /** Stock empty: measurements arrive as they are taken. */
    Live,
    /** Link lost, retrying before going back to scanning. */
    Reconnecting,
    Publishing,
    Stopped,
    Error,
}
