package com.otto.launcher.core.http

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
