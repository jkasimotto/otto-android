package com.otto.launcher.core.config

internal object OttoConfig {
    private const val BUILD_CONFIG_CLASS = "com.otto.launcher.BuildConfig"

    val groqApiKey: String by lazy {
        readBuildConfigField("GROQ_API_KEY") ?: System.getenv("GROQ_API_KEY").orEmpty()
    }

    val hasGroqKey: Boolean get() = groqApiKey.isNotBlank()

    /** GitHub "owner/name" the updater and feedback target; falls back to the upstream repo. */
    val githubRepo: String by lazy {
        (readBuildConfigField("OTTO_GITHUB_REPO") ?: "").ifBlank { "jkasimotto/otto-android" }
    }

    val githubFeedbackToken: String by lazy {
        readBuildConfigField("GITHUB_FEEDBACK_TOKEN")
            ?: System.getenv("GITHUB_FEEDBACK_TOKEN").orEmpty()
    }

    private fun readBuildConfigField(name: String): String? = runCatching {
        val clazz = Class.forName(BUILD_CONFIG_CLASS)
        clazz.getField(name).get(null) as? String
    }.getOrNull()
}
