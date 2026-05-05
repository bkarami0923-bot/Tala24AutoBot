package com.tala24.autobot

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class TelegramBot(
    private val token: String,
    private val chatId: String
) {

    private val TAG = "Tala24-TelegramBot"

    // کلاینت تلگرام بدون پروکسی (اتصال مستقیم)
    private val telegramClient: OkHttpClient = OkHttpClient.Builder()
        .build()

    fun sendMessage(text: String): Boolean {
        return try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val body = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .add("parse_mode", "HTML")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            telegramClient.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string()
                Log.d(TAG, "Telegram response: code=${resp.code}, body=$respBody")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}", e)
            false
        }
    }
}
