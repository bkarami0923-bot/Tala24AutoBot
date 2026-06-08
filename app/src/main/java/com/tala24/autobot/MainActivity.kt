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
        const val KEY_SEND_TIME = "send_time"   // ⬅️ جدید
    }

    private val TAG = "Tala24"

    private lateinit var txtStatus: TextView
    private lateinit var txtLastMessage: TextView
    private lateinit var edtToken: EditText
    private lateinit var edtChatId: EditText
    private lateinit var edtSendTime: EditText   // ⬅️ تغییر نام
    private lateinit var btnSaveSettings: Button
    private lateinit var btnSendOnce: Button
    private lateinit var btnStartAuto: Button
    private lateinit var btnStopAuto: Button

    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var autoJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtLastMessage = findViewById(R.id.txtLastMessage)
        edtToken = findViewById(R.id.edtToken)
        edtChatId = findViewById(R.id.edtChatId)
        edtSendTime = findViewById(R.id.edtInterval)   // همان فیلد قبلی، فقط کاربرد جدید
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnSendOnce = findViewById(R.id.btnSendOnce)
        btnStartAuto = findViewById(R.id.btnStartAuto)
        btnStopAuto = findViewById(R.id.btnStopAuto)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedToken = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        val savedChatId = prefs.getString(KEY_CHAT_ID, "") ?: ""
        val savedSendTime = prefs.getString(KEY_SEND_TIME, "12:00") ?: "12:00"

        if (savedToken.isNotEmpty()) edtToken.setText(savedToken)
        if (savedChatId.isNotEmpty()) edtChatId.setText(savedChatId)
        edtSendTime.setText(savedSendTime)

        txtStatus.text = "Status: Settings loaded"

        btnSaveSettings.setOnClickListener {
            val token = edtToken.text.toString().trim()
            val chatId = edtChatId.text.toString().trim()
            val sendTime = edtSendTime.text.toString().trim()   // ⬅️ ساعت

            if (token.isEmpty() || chatId.isEmpty() || sendTime.isEmpty()) {
                txtStatus.text = "Status: Please fill all fields"
                return@setOnClickListener
            }

            prefs.edit()
                .putString(KEY_BOT_TOKEN, token)
                .putString(KEY_CHAT_ID, chatId)
                .putString(KEY_SEND_TIME, sendTime)
                .apply()

            Log.d(TAG, "Settings saved: token=${token.take(5)}..., chatId=$chatId, time=$sendTime")
            txtStatus.text = "Status: Settings saved"
        }

        btnSendOnce.setOnClickListener { sendOnce() }
        btnStartAuto.setOnClickListener { startAuto() }
        btnStopAuto.setOnClickListener { stopAuto() }
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
        val sendTime = prefs.getString(KEY_SEND_TIME, "12:00") ?: "12:00"

        if (token.isEmpty() || chatId.isEmpty()) {
            txtStatus.text = "Status: Please complete settings first"
            return
        }

        txtStatus.text = "Status: Auto sending enabled at $sendTime daily"
        Log.d(TAG, "Auto sending started, time=$sendTime")

        autoJob = uiScope.launch(Dispatchers.IO) {
            val fetcher = PriceFetcher()

            while (isActive) {

                val (targetHour, targetMin) = sendTime.split(":").map { it.toInt() }

                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMin)
                    set(Calendar.SECOND, 0)
                }

                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }

                val waitMs = target.timeInMillis - now.timeInMillis
                delay(waitMs)

                val pricesText = fetcher.getPricesText()
                val bot = TelegramBot(token, chatId)
                val ok = bot.sendMessage(pricesText)

                withContext(Dispatchers.Main) {
                    if (ok) {
                        txtStatus.text = "Status: Auto message sent"
                        txtLastMessage.text = pricesText
                    } else {
                        txtStatus.text = "Status: Error in auto sending"
                    }
                }

                delay(24 * 60 * 60 * 1000L)
            }
        }
    }

    private fun stopAuto() {
        autoJob?.cancel()
        autoJob = null
        txtStatus.text = "Status: Auto sending stopped"
    }

    override fun onDestroy() {
        super.onDestroy()
        autoJob?.cancel()
    }
}
