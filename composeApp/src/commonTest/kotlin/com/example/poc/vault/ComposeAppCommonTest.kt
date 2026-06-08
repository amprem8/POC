package com.example.poc.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun normalizesWebOriginsToRootDomain() {
        assertEquals("example.com", rootDomainOrPackage("https://www.accounts.example.com/login"))
        assertEquals("example.co.uk", rootDomainOrPackage("https://www.login.example.co.uk/signin"))
    }

    @Test
    fun preservesAndroidPackageNames() {
        assertEquals("com.example.app", rootDomainOrPackage("com.example.app"))
        assertEquals("App", originDisplayName("com.example.app"))
    }

    @Test
    fun matchesEquivalentWebDomains() {
        assertTrue(originsMatch("https://accounts.google.com", "google.com"))
        assertTrue(originsMatch("https://www.retail.example.co.uk", "checkout.example.co.uk"))
        assertFalse(originsMatch("google.com", "example.com"))
    }

    @Test
    fun matchesPackageNamesExactly() {
        assertTrue(originsMatch("com.example.bank", "com.example.bank"))
        assertFalse(originsMatch("com.example.bank", "com.example.other"))
    }
}