package com.example.poc

private val multiLabelSuffixes = setOf(
    "ac.uk",
    "co.jp",
    "co.kr",
    "co.nz",
    "co.uk",
    "com.au",
    "com.br",
    "com.mx",
    "com.tr",
    "com.sg",
    "com.cn",
    "net.au",
    "org.uk",
)

fun normalizeCredentialOrigin(raw: String?): String {
    val candidate = raw
        ?.trim()
        ?.lowercase()
        .orEmpty()
        .removePrefix("androidapp://")
        .substringBefore('#')
        .substringBefore('?')
        .let { value -> value.substringAfter("//", missingDelimiterValue = value) }
        .let { value -> value.substringAfter('@', missingDelimiterValue = value) }
        .substringBefore('/')
        .substringBefore(':')
        .removePrefix("www.")
        .trim('.')

    return candidate
}

fun rootDomainOrPackage(raw: String?): String {
    val normalized = normalizeCredentialOrigin(raw)
    if (normalized.isBlank()) return ""
    if (!normalized.contains('.')) return normalized

    val labels = normalized.split('.').filter { it.isNotBlank() }
    if (labels.size < 2) return normalized

    if (looksLikePackageName(normalized)) {
        return normalized
    }

    val lastTwo = labels.takeLast(2).joinToString(".")
    val lastThree = labels.takeLast(3).joinToString(".")
    return if (lastTwo in multiLabelSuffixes && labels.size >= 3) lastThree else lastTwo
}

fun originsMatch(savedOrigin: String?, requestOrigin: String?): Boolean {
    val savedNormalized = normalizeCredentialOrigin(savedOrigin)
    val requestNormalized = normalizeCredentialOrigin(requestOrigin)
    if (savedNormalized.isBlank() || requestNormalized.isBlank()) return false
    if (savedNormalized == requestNormalized) return true

    val savedIsPackage = looksLikePackageName(savedNormalized)
    val requestIsPackage = looksLikePackageName(requestNormalized)
    if (savedIsPackage || requestIsPackage) {
        return savedNormalized == requestNormalized
    }

    return rootDomainOrPackage(savedNormalized) == rootDomainOrPackage(requestNormalized)
}

fun originDisplayName(raw: String?): String {
    val normalized = normalizeCredentialOrigin(raw)
    if (normalized.isBlank()) return "Unknown"
    val label = if (looksLikePackageName(normalized)) {
        normalized.substringAfterLast('.')
    } else {
        rootDomainOrPackage(normalized).substringBefore('.')
    }
    return label.replaceFirstChar { it.uppercase() }
}

private fun looksLikePackageName(value: String): Boolean {
    if (!value.contains('.')) return false
    val labels = value.split('.')
    if (labels.size < 3) return false
    if (labels.any { it.isBlank() }) return false
    if (labels.any { it.any(Char::isUpperCase) }) return false
    if (labels.any { label -> label.any { !(it.isLowerCase() || it.isDigit() || it == '_') } }) return false

    val reverseDomainPrefixes = setOf("com", "org", "net", "io", "dev", "app", "me")
    return labels.first() in reverseDomainPrefixes
}



