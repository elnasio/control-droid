package com.mories.control_droid.ui

enum class NavigationTarget {
    RoleSelection, Home, Pair, Control, Preview;

    /** Rute dasar, otomatis dari nama enum (lowercase) */
    val route: String
        get() = name.lowercase()

    /** Buat pola rute dengan parameter, contoh: control/{id} */
    fun withParam(vararg paramNames: String): String = buildString {
        append(route)
        paramNames.forEach { append("/{$it}") }
    }

    /** Buat rute dengan nilai argumen, contoh: control/abc123 */
    fun withArg(vararg args: String): String = buildString {
        append(route)
        args.forEach { append("/$it") }
    }

    companion object {
        /** Ambil enum dari route string, misalnya "control/abc" -> Control */
        fun fromRoute(route: String): NavigationTarget? =
            entries.firstOrNull { route.startsWith(it.route) }
    }
}