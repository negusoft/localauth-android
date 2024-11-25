package com.negusoft.localauth.ui.common

data class ErrorModel(
    val dismiss: () -> Unit
)
data class RetryErrorModel(
    val retry: () -> Unit,
    val dismiss: () -> Unit
)