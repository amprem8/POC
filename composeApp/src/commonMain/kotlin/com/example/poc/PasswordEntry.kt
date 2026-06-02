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
    /** Google Favicon API URL for this entry's domain. */
    val faviconUrl: String
        get() {
            val domain = loginUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .substringBefore(':')
            return if (domain.isNotBlank()) "https://www.google.com/s2/favicons?domain=$domain&sz=128" else ""
        }
}

