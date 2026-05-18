package com.example.poc

interface PlatformServices {
    val serverBaseUrl: String
    val biometricAvailable: Boolean

    fun loadPassKeyConfig(): PassKeyConfig?

    fun savePassKeyConfig(config: PassKeyConfig)

    suspend fun promptBiometric(promptTitle: String, promptSubtitle: String): Boolean

    fun copyToClipboard(label: String, value: String)

    fun saveRecoveryTextFile(fileName: String, content: String): String
}

class PreviewPlatformServices : PlatformServices {
    private var config: PassKeyConfig? = null

    override val serverBaseUrl: String = "http://localhost:$SERVER_PORT"
    override val biometricAvailable: Boolean = true

    override fun loadPassKeyConfig(): PassKeyConfig? = config

    override fun savePassKeyConfig(config: PassKeyConfig) {
        this.config = config
    }

    override suspend fun promptBiometric(promptTitle: String, promptSubtitle: String): Boolean = true

    override fun copyToClipboard(label: String, value: String) = Unit

    override fun saveRecoveryTextFile(fileName: String, content: String): String = "Preview saved: $fileName"
}
