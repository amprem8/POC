package com.example.poc.vault

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform