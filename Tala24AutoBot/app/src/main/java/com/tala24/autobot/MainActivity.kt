package com.tala24.autobot

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "tala24_prefs"
        const val KEY_BOT_TOKEN = "bot_token"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_INTERVAL_MIN = "interval_min"
    }

    private val TAG = "Tala24"

    private lateinit var txtStatus: TextView
    private lateinit var txtLastMessage: TextView
    private lateinit var edtToken: EditText
    private lateinit var edtChatId: EditText
    private lateinit var edtInterval: EditText
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
        edtInterval = findViewById(R.id.edtInterval)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnSendOnce = findViewById(R.id.btnSendOnce)
        btnStartAuto = findViewById(R.id.btnStartAuto)
        btnStopAuto = findViewById(R.id.btnStopAuto)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedToken = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        val savedChatId = prefs.getString(KEY_CHAT_ID, "") ?: ""
        val savedInterval = prefs.getInt(KEY_INTERVAL_MIN, 30)

        if (savedToken.isNotEmpty()) edtToken.setText(savedToken)
        if (savedChatId.isNotEmpty()) edtChatId.setText(savedChatId)
        edtInterval.setText(savedInterval.toString())

        txtStatus.text = "وضعیت: تنظیمات بارگذاری شد"

        btnSaveSettings.setOnClickListener {
            val token = edtToken.text.toString().trim()
            val chatId = edtChatId.text.toString().trim()
            val intervalText = edtInterval.text.toString().trim()

            if (token.isEmpty() || chatId.isEmpty() || intervalText.isEmpty()) {
                txtStatus.text = "وضعیت: لطفاً همه فیلدها را پر کنید"
                return@setOnClickListener
            }

            val intervalMin = intervalText.toIntOrNull() ?: 30

            prefs.edit()
                .putString(KEY_BOT_TOKEN, token)
                .putString(KEY_CHAT_ID, chatId)
                .putInt(KEY_INTERVAL_MIN, intervalMin)
                .apply()

            Log.d(TAG, "Settings saved: token=${token.take(5)}..., chatId=$chatId, interval=$intervalMin")
            txtStatus.text = "وضعیت: تنظیمات ذخیره شد"
        }

        btnSendOnce.setOnClickListener {
            sendOnce()
        }

        btnStartAuto.setOnClickListener {
            startAuto()
        }

        btnStopAuto.setOnClickListener {
            stopAuto()
        }
    }

    private fun sendOnce() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        val chatId = prefs.getString(KEY_CHAT_ID, "") ?: ""

        if (token.isEmpty() || chatId.isEmpty()) {
            txtStatus.text = "وضعیت: ابتدا توکن و Chat ID را ذخیره کنید"
            return
        }

        txtStatus.text = "وضعیت: در حال استخراج قیمت و ارسال..."
        Log.d(TAG, "Manual sendOnce triggered")

        uiScope.launch(Dispatchers.IO) {
            val fetcher = PriceFetcher()
            val pricesText = fetcher.getPricesText()
            val bot = TelegramBot(token, chatId)
            val ok = bot.sendMessage(pricesText)

            withContext(Dispatchers.Main) {
                if (ok) {
                    txtStatus.text = "وضعیت: پیام ارسال شد"
                    txtLastMessage.text = pricesText
                    Log.d(TAG, "Manual sendOnce success")
                } else {
                    txtStatus.text = "وضعیت: خطا در ارسال پیام"
                    Log.e(TAG, "Manual sendOnce failed")
                }
            }
        }
    }

    private fun startAuto() {
        if (autoJob != null) {
            txtStatus.text = "وضعیت: ارسال خودکار از قبل فعال است"
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val intervalMin = prefs.getInt(KEY_INTERVAL_MIN, 30)
        val token = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        val chatId = prefs.getString(KEY_CHAT_ID, "") ?: ""

        if (token.isEmpty() || chatId.isEmpty()) {
            txtStatus.text = "وضعیت: ابتدا تنظیمات را کامل کنید"
            return
        }

        txtStatus.text = "وضعیت: ارسال خودکار هر $intervalMin دقیقه فعال شد"
        Log.d(TAG, "Auto sending started, interval=$intervalMin min")

        autoJob = uiScope.launch(Dispatchers.IO) {
            val fetcher = PriceFetcher()
            val localPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            while (isActive) {
                val currentToken = localPrefs.getString(KEY_BOT_TOKEN, "") ?: ""
                val currentChatId = localPrefs.getString(KEY_CHAT_ID, "") ?: ""

                if (currentToken.isEmpty() || currentChatId.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        txtStatus.text = "وضعیت: تنظیمات ناقص است، ارسال خودکار متوقف شد"
                    }
                    Log.e(TAG, "Auto send stopped due to missing settings")
                    break
                }

                val pricesText = fetcher.getPricesText()
                val bot = TelegramBot(currentToken, currentChatId)
                val ok = bot.sendMessage(pricesText)

                withContext(Dispatchers.Main) {
                    if (ok) {
                        txtStatus.text = "وضعیت: پیام خودکار ارسال شد"
                        txtLastMessage.text = pricesText
                        Log.d(TAG, "Auto send success")
                    } else {
                        txtStatus.text = "وضعیت: خطا در ارسال خودکار"
                        Log.e(TAG, "Auto send failed")
                    }
                }

                delay(intervalMin * 60_000L)
            }
        }
    }

    private fun stopAuto() {
        autoJob?.cancel()
        autoJob = null
        txtStatus.text = "وضعیت: ارسال خودکار متوقف شد"
        Log.d(TAG, "Auto sending stopped by user")
    }

    override fun onDestroy() {
        super.onDestroy()
        autoJob?.cancel()
    }
}
