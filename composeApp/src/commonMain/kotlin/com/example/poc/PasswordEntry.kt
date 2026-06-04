package com.example.poc

data class PasswordEntry(
    val id: String,
    val siteName: String,
    val username: String,
    val password: String,
    val loginUrl: String,
    val dateModified: Long, // epoch millis
    val notes: String = "",
) {
    /** Extracted domain for favicon lookups. */
    val domain: String
        get() = loginUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore(':')

    /** Tier 1 — DuckDuckGo Icons API (highest coverage, returns actual site favicons). */
    val faviconUrl: String
        get() = if (domain.isNotBlank()) "https://icons.duckduckgo.com/ip3/$domain.ico" else ""

    /** Tier 2 — Google Favicon API (secondary fallback, good quality). */
    val fallbackFaviconUrl: String
        get() = if (domain.isNotBlank()) "https://www.google.com/s2/favicons?domain=$domain&sz=128" else ""
}

