package com.tala24.autobot

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.DecimalFormat
import java.util.Calendar

class PriceFetcher {

    private val TAG = "Tala24-PriceFetcher"

    private val iranClient: OkHttpClient = OkHttpClient.Builder().build()
    private val df = DecimalFormat("#,###")

    // ------------------ HTML Fetch ------------------

    private fun fetchHtml(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            iranClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "HTTP error for $url: ${resp.code}")
                    null
                } else resp.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchHtml error: ${e.message}")
            null
        }
    }

    // ------------------ Helpers ------------------

    private fun normalizeDigits(s0: String): String {
        var s = s0
        val p = listOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')
        val a = listOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')
        for (i in 0..9) {
            s = s.replace(p[i], ('0' + i))
            s = s.replace(a[i], ('0' + i))
        }
        return s
    }

    private fun parsePriceToLong(raw: String?): Long? {
        if (raw == null) return null
        return try {
            var s = normalizeDigits(raw)
            s = s.replace(",", "")
                .replace("٫", "")
                .replace(".", "")
                .replace("تومان", "")
                .replace("ت", "")
                .replace("$", "")
                .replace(" ", "")
            if (s.isEmpty()) null else s.toLong()
        } catch (_: Exception) {
            null
        }
    }

    private fun formatPrice(v: Long?): String {
        if (v == null) return "—"
        return df.format(v)
    }

    // ------------------ تاریخ شمسی ------------------

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var gy2 = gy - 1600
        var gm2 = gm - 1
        var gd2 = gd - 1
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        gDayNo += gdm[gm2] + gd2
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) gDayNo++

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        val jm = if (jDayNo < 186) 1 + jDayNo / 31 else 7 + (jDayNo - 186) / 30
        val jd = 1 + if (jDayNo < 186) jDayNo % 31 else (jDayNo - 186) % 30
        return Triple(jy, jm, jd)
    }

    private fun getJalaliDateAndTime(): Pair<String, String> {
        val c = Calendar.getInstance()
        val (jy, jm, jd) = gregorianToJalali(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
        val date = "%04d/%02d/%02d".format(jy, jm, jd)
        val time = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        return Pair(date, time)
    }

    // ------------------ Data Class ------------------

    data class Prices(
        val usdTehran: Long?,
        val ounce: Long?,
        val brent: Long?,
        val gold18: Long?,
        val gold24: Long?,
        val coinImami: Long?,
        val coinBahar: Long?,
        val coinHalf: Long?,
        val coinQuarter: Long?,
        val coinGerami: Long?,
        val silver: Long?
    )

    // ------------------ Fetch Functions ------------------

    private fun fetchUsdTehran(): Long? {
        val url = "https://nobitex.ir/price/usdt/"
        val html = fetchHtml(url) ?: return null

        return try {
            val doc = Jsoup.parse(html)
            val title = doc.selectFirst("h1.text-headline-medium")?.text()?.trim()
            if (title?.contains("قیمت تتر") != true) return null

            val priceSpan = doc.selectFirst("span.text-body-large")?.text()?.trim()
                ?: return null

            val cleaned = normalizeDigits(priceSpan)
                .replace(",", "")
                .trim()

            cleaned.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "fetchUsdTehran error: ${e.message}")
            null
        }
    }

    private fun fetchOunce(): Long? {
        val url = "https://www.estjt.ir/"
        val html = fetchHtml(url) ?: return null

        return try {
            val doc = Jsoup.parse(html)
            val rows = doc.select("tr")

            for (row in rows) {
                val name = row.selectFirst("td.name")?.text()?.trim()
                if (name == "انس طلا") {

                    val priceTd = row.selectFirst("td.price")?.text()?.trim()
                    if (priceTd != null) {

                        val cleaned = normalizeDigits(priceTd)
                            .replace("$", "")
                            .replace(" ", "")
                            .trim()

                        return cleaned.toLongOrNull()
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "fetchOunce error: ${e.message}")
            null
        }
    }

    private fun fetchBrent(): Long? {
        val url = "https://servatmandi.com/Entity/Summary/10000000002001"
        val html = fetchHtml(url) ?: return null

        return try {
            val doc = Jsoup.parse(html)

            val ths = doc.select("th")
            var price: String? = null

            for (th in ths) {
                if (th.text().trim() == "آخرین") {
                    val td = th.parent()?.selectFirst("td#Close")
                    price = td?.text()
                    break
                }
            }

            if (price == null) return null

            val cleaned = normalizeDigits(price)
                .replace(",", "")
                .trim()

            val value = cleaned.toDoubleOrNull() ?: return null

            (value * 100).toLong()
        } catch (e: Exception) {
            Log.e(TAG, "fetchBrent error: ${e.message}")
            null
        }
    }

    private fun fetchFromEstjt(nameText: String): Long? {
        val html = fetchHtml("https://www.estjt.ir/") ?: return null

        return try {
            val doc = Jsoup.parse(html)
            val rows = doc.select("tr")

            for (row in rows) {
                val name = row.selectFirst("td.name")?.text()?.trim()
                val price = row.selectFirst("td.price")?.text()

                if (name == nameText) {
                    return parsePriceToLong(price)
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "fetchFromEstjt error: ${e.message}")
            null
        }
    }

    private fun fetchGold18() = fetchFromEstjt("طلا ۱۸ عیار")
    private fun fetchGold24() = fetchFromEstjt("طلای ۲۴ عیار")
    private fun fetchCoinImami() = fetchFromEstjt("سکه طرح جدید")
    private fun fetchCoinBahar() = fetchFromEstjt("سکه طرح قدیم")
    private fun fetchCoinHalf() = fetchFromEstjt("نیم سکه")
    private fun fetchCoinQuarter() = fetchFromEstjt("ربع سکه")
    private fun fetchCoinGerami() = fetchFromEstjt("سکه گرمی")

    private fun fetchSilver(): Long? {
        val url = "https://wallex.ir/silver"
        val html = fetchHtml(url) ?: return null

        return try {
            val doc = Jsoup.parse(html)
            val span = doc.selectFirst("span.MuiTypography-root.MuiTypography-TitleStrong.mui-17xixml")
            parsePriceToLong(span?.text())
        } catch (e: Exception) {
            Log.e(TAG, "fetchSilver error: ${e.message}")
            null
        }
    }

    // ------------------ NEW: Round last digit to zero ------------------

    private fun roundToTens(v: Long?): Long? {
        if (v == null) return null
        return (v / 10) * 10
    }

    // ------------------ Collect All ------------------

    private fun getAllPrices(): Prices {
        return Prices(
            usdTehran = fetchUsdTehran(),
            ounce = fetchOunce(),
            brent = fetchBrent(),
            gold18 = fetchGold18(),
            gold24 = fetchGold24(),
            coinImami = fetchCoinImami(),
            coinBahar = fetchCoinBahar(),
            coinHalf = fetchCoinHalf(),
            coinQuarter = fetchCoinQuarter(),
            coinGerami = fetchCoinGerami(),
            silver = fetchSilver()
        )
    }

    // ------------------ Final Output ------------------

    fun getPricesText(): String {
        val p = getAllPrices()
        val (jDate, jTime) = getJalaliDateAndTime()

        // Apply rounding ONLY to usdTehran and silver
        val usdRounded = roundToTens(p.usdTehran)
        val silverRounded = roundToTens(p.silver)

        val brentStr = p.brent?.let { String.format("%,.2f", it.toDouble() / 100) } ?: "—"

        return """
🗓 $jDate
⏰ $jTime
━━━━━━━━━━━━━━━━━━━━━
💵 دلار تهران : <b>${formatPrice(usdRounded)}</b> تومان
━━━━━━━━━━━━━━━━━━━━━
💰 انس : <b>${formatPrice(p.ounce)}</b> $

🛢 نفت برنت : <b>$brentStr</b> $
━━━━━━━━━━━━━━━━━━━━━
🟡 طلای ۱۸ عیار : <b>${formatPrice(p.gold18)}</b> تومان

🟡 طلای ۲۴ عیار : <b>${formatPrice(p.gold24)}</b> تومان
━━━━━━━━━━━━━━━━━━━━━
🔶 سکه امامی : <b>${formatPrice(p.coinImami)}</b> تومان

🔶 سکه بهار آزادی : <b>${formatPrice(p.coinBahar)}</b> تومان

🔶 نیم سکه : <b>${formatPrice(p.coinHalf)}</b> تومان

🔶 ربع سکه : <b>${formatPrice(p.coinQuarter)}</b> تومان

🔶 سکه گرمی : <b>${formatPrice(p.coinGerami)}</b> تومان
━━━━━━━━━━━━━━━━━━━━━
⚪ نقره : <b>${formatPrice(silverRounded)}</b> تومان

📊 @Tala24_B
        """.trimIndent()
    }
}
