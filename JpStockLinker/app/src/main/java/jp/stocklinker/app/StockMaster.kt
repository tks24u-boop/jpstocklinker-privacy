package jp.stocklinker.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// マスターデータ用（検索候補表示用）v5.3: market追加
data class MasterStockItem(
    val code: String,
    val name: String,
    val nameKana: String = "",
    val sector: String = "",
    val market: String = "",
    val themes: List<String> = emptyList()
)

// マスターデータJSON構造
data class StockMasterData(
    val version: String,
    val source: String = "",
    val count: Int = 0,
    val stocks: List<MasterStockItem>
)

// マスターデータリポジトリ（シングルトン）
object StockMasterRepository {
    private var masterList: List<MasterStockItem> = emptyList()
    private var isLoaded = false

    fun load(context: Context) {
        if (isLoaded) return
        try {
            val json = context.assets.open("stock_master.json")
                .bufferedReader().use { it.readText() }
            val data = Gson().fromJson(json, StockMasterData::class.java)
            masterList = data.stocks
            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
            masterList = emptyList()
        }
    }

    fun search(query: String, limit: Int = 20): List<MasterStockItem> {
        if (query.isEmpty() || query.length < 2) return emptyList()
        
        val qLower = query.lowercase()
        val qHira = katakanaToHiragana(query)
        val qKata = hiraganaToKatakana(query)

        return masterList.filter { item ->
            item.code.contains(query, ignoreCase = true) ||
            item.name.lowercase().contains(qLower) ||
            item.nameKana.contains(qKata) ||
            katakanaToHiragana(item.nameKana).contains(qHira) ||
            katakanaToHiragana(item.name).contains(qHira) ||
            hiraganaToKatakana(item.name).contains(qKata) ||
            item.sector.lowercase().contains(qLower) ||
            item.market.lowercase().contains(qLower) ||
            item.themes.any { it.lowercase().contains(qLower) }
        }.take(limit)
    }

    fun findByCode(code: String): MasterStockItem? {
        return masterList.find { it.code == code }
    }

    fun getCount(): Int = masterList.size

    private fun hiraganaToKatakana(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            if (c in 'ぁ'..'ん') sb.append((c.code + 0x60).toChar())
            else sb.append(c)
        }
        return sb.toString()
    }

    private fun katakanaToHiragana(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            if (c in 'ァ'..'ン') sb.append((c.code - 0x60).toChar())
            else sb.append(c)
        }
        return sb.toString()
    }
}
