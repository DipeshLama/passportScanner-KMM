package com.example.passportscannerkmm

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform