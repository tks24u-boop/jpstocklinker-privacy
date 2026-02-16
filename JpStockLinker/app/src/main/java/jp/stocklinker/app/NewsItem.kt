package jp.stocklinker.app

/**
 * ニュースアイテムのデータクラス
 * 既存のStockItem等とは完全に独立
 */
data class NewsItem(
    val title: String,
    val link: String,
    val pubDate: String,
    val source: String,
    val category: NewsCategory
)

/**
 * ニュースのカテゴリ
 */
enum class NewsCategory {
    ECONOMIC_NEWS,      // 経済ニュース
    TIMELY_DISCLOSURE   // 適時開示
}
