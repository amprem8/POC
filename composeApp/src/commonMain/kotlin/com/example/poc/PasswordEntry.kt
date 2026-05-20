package com.example.poc

data class PasswordEntry(
    val id: String,
    val siteName: String,
    val username: String,
    val password: String,
    val loginUrl: String,
    val dateModified: Long, // epoch millis
)

