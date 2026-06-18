package com.tala24.autobot

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "tala24_prefs"
        const val KEY_BOT_TOKEN = "bot_token"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_HOUR = "send_hour"
        const val KEY_MINUTE = "send_minute"
    }

    private val TAG = "Tala24"

    private lateinit var txtStatus: TextView
    private lateinit var txtLastMessage: TextView
    private lateinit var edtToken: EditText
    private lateinit var edtChatId: EditText
    private lateinit var edtHour: EditText
    private lateinit var edtMinute: EditText
    private lateinit var btnSaveSettings: Button
    private lateinit var btnSendOnce: Button
    private lateinit var btnStartAuto: Button

    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var autoJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtLastMessage = findViewById(R.id.txtLastMessage)
        edtToken = findViewById(R.id.edtToken)
        edtChatId = findViewById(R.id.edtChatId)
        edtHour = findViewById(R.id.edtHour)
        edtMinute = findViewById(R.id.edtMinute)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnSendOnce = findViewById(R.id.btnSendOnce)
        btnStartAuto = findViewById(R.id.btnStartAuto)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        edtToken.setText(prefs.getString(KEY_BOT_TOKEN, "") ?: "")
        edtChatId.setText(prefs.getString(KEY_CHAT_ID, "") ?: "")
        edtHour.setText(prefs.getInt(KEY_HOUR, 12).toString())
        edtMinute.setText(prefs.getInt(KEY_MINUTE, 0).toString())

        txtStatus.text = "Status: Settings loaded"

        btnSaveSettings.setOnClickListener {
            val token = edtToken.text.toString().trim()
            val chatId = edtChatId.text.toString().trim()
            val hourText = edtHour.text.toString().trim()
            val minuteText = edtMinute.text.toString().trim()

            if (token.isEmpty() || chatId.isEmpty() || hourText.isEmpty() || minuteText.isEmpty()) {
                txtStatus.text = "Status: Please fill all fields"
                return@setOnClickListener
            }

            val hour = hourText.toIntOrNull() ?: 12
            val minute = minuteText.toIntOrNull() ?: 0

            prefs.edit()
                .putString(KEY_BOT_TOKEN, token)
                .putString(KEY_CHAT_ID, chatId)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply()

            txtStatus.text = "Status: Settings saved"
        }

        btnSendOnce.setOnClickListener {
            sendOnce()
        }

        btnStartAuto.setOnClickListener {
            startAuto()
        }
    }

    private fun sendOnce() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        val chatId = prefs.getString(KEY_CHAT_ID, "") ?: ""

        if (token.isEmpty() || chatId.isEmpty()) {
            txtStatus.text = "Status: Please save token and Chat ID first"
            return
        }

        txtStatus.text = "Status: Fetching prices and sending..."
        Log.d(TAG, "Manual sendOnce triggered")

        uiScope.launch(Dispatchers.IO) {
            val fetcher = PriceFetcher()
            val pricesText = fetcher.getPricesText()
            val bot = TelegramBot(token, chatId)
            val ok = bot.sendMessage(pricesText)

            withContext(Dispatchers.Main) {
                if (ok) {
                    txtStatus.text = "Status: Message sent"
                    txtLastMessage.text = pricesText
                } else {
                    txtStatus.text = "Status: Error sending message"
                }
            }
        }
    }

    private fun startAuto() {
        if (autoJob != null) {
            txtStatus.text = "Status: Auto sending is already active"
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        val chatId = prefs.getString(KEY_CHAT_ID, "") ?: ""
        val hour = prefs.getInt(KEY_HOUR, 12)
        val minute = prefs.getInt(KEY_MINUTE, 0)

        if (token.isEmpty() || chatId.isEmpty()) {
            txtStatus.text = "Status: Please complete settings first"
            return
        }

        txtStatus.text = "Status: Scheduled sending enabled at %02d:%02d".format(hour, minute)

        autoJob = uiScope.launch(Dispatchers.IO) {
            val fetcher = PriceFetcher()

            while (isActive) {
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }

                if (now.after(target)) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }

                val delayMillis = target.timeInMillis - now.timeInMillis
                delay(delayMillis)

                val pricesText = fetcher.getPricesText()
                val bot = TelegramBot(token, chatId)
                val ok = bot.sendMessage(pricesText)

                withContext(Dispatchers.Main) {
                    if (ok) {
                        txtStatus.text = "Status: Scheduled message sent"
                        txtLastMessage.text = pricesText
                    } else {
                        txtStatus.text = "Status: Error in scheduled sending"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoJob?.cancel()
    }
}
