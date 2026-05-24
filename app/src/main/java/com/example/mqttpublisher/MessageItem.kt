package com.example.mqttpublisher

data class MessageItem(
    val time: String,
    val payload: String,
    val count: Int,
    val success: Boolean
)
