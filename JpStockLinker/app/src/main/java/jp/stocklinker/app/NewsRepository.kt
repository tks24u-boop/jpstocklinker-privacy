package jp.stocklinker.app

import android.util.Log
import com.prof18.rssparser.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ニュース取得リポジトリ
 * 既存のStockMasterRepositoryとは完全に独立
 */
object NewsRepository {
    
    private const val TAG = "NewsRepository"
    
    // Google News 経済・株式
    private const val URL_GOOGLE_NEWS = 
        "https://news.google.com/rss/search?q=株式+経済+日経平均&hl=ja&gl=JP&ceid=JP:ja"
    
    // 株探 ニュース
    private const val URL_KABUTAN_NEWS = 
        "https://kabutan.jp/rss/news"
    
    private val parser = RssParser()
    
    /**
     * 経済ニュースを取得
     * @param limit 取得件数上限
     * @return ニュースリスト（エラー時は空リスト）
     */
    suspend fun fetchEconomicNews(limit: Int = 10): List<NewsItem> {
        return withContext(Dispatchers.IO) {
            try {
                val channel = parser.getRssChannel(URL_GOOGLE_NEWS)
                channel.items.take(limit).map { item ->
                    NewsItem(
                        title = item.title?.take(100) ?: "",
                        link = item.link ?: "",
                        pubDate = formatDate(item.pubDate),
                        source = extractSource(item.title),
                        category = NewsCategory.ECONOMIC_NEWS
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch economic news", e)
                emptyList()
            }
        }
    }
    
    /**
     * 適時開示速報を取得
     * @param limit 取得件数上限
     * @return ニュースリスト（エラー時は空リスト）
     */
    suspend fun fetchTimelyDisclosure(limit: Int = 10): List<NewsItem> {
        return withContext(Dispatchers.IO) {
            try {
                val channel = parser.getRssChannel(URL_KABUTAN_NEWS)
                channel.items.take(limit).map { item ->
                    NewsItem(
                        title = item.title?.take(100) ?: "",
                        link = item.link ?: "",
                        pubDate = formatDate(item.pubDate),
                        source = "株探",
                        category = NewsCategory.TIMELY_DISCLOSURE
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch timely disclosure", e)
                emptyList()
            }
        }
    }
    
    /**
     * 日付フォーマット
     */
    private fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return ""
        return try {
            // 簡易的な日付抽出（必要に応じて調整）
            dateStr.take(16)
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * ソース抽出（Google Newsの場合、タイトル末尾にソースがある）
     */
    private fun extractSource(title: String?): String {
        if (title.isNullOrEmpty()) return ""
        val lastDash = title.lastIndexOf(" - ")
        return if (lastDash > 0) {
            title.substring(lastDash + 3).take(20)
        } else {
            ""
        }
    }
}
