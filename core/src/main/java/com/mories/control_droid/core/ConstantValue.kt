package com.mories.control_droid.core

object ConstantValue {
    const val PORT_VALUE = 8080

    /**
     * Placeholder base URL for the cloud relay backend described in
     * docs/internet-relay-api.md — that backend does not exist yet. Uses the `.example` TLD
     * (reserved for documentation, RFC 2606) so it can never resolve to a real service; every
     * [com.mories.control_droid.core.networking.InternetRelayClient] call against it simply fails
     * with a connection error until a real relay URL replaces it here.
     */
    const val INTERNET_RELAY_BASE_URL = "https://relay.controldroid.example"
}