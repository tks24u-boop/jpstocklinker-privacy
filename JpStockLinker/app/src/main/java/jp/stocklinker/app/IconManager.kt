package jp.stocklinker.app

/**
 * アイコン管理シングルトン
 * 文字列キーとリソースIDの対応を管理し、後方互換性を提供
 */
object IconManager {
    
    // アイコンキーとリソースIDの対応表
    private val iconMap: Map<String, Int> = mapOf(
        // システム系（グループ用）
        "watching" to R.drawable.ic_watching,
        "holding" to R.drawable.ic_holding,
        "considering" to R.drawable.ic_considering,
        "sold" to R.drawable.ic_sold,
        "all" to R.drawable.ic_all,
        
        // ユーザー選択用
        "star" to R.drawable.ic_star,
        "fire" to R.drawable.ic_fire,
        "diamond" to R.drawable.ic_diamond,
        "flag" to R.drawable.ic_flag,
        "chart" to R.drawable.ic_chart,
        "rocket" to R.drawable.ic_rocket,
        "favorite" to R.drawable.ic_favorite,
        "bolt" to R.drawable.ic_bolt,
        
        // UI用
        "refresh" to R.drawable.ic_refresh,
        "search" to R.drawable.ic_search,
        "arrow_back" to R.drawable.ic_arrow_back,
        "filter_list" to R.drawable.ic_filter_list,
        "add" to R.drawable.ic_add,
        "delete" to R.drawable.ic_delete,
        "edit" to R.drawable.ic_edit,
        "folder" to R.drawable.ic_folder,
        "note" to R.drawable.ic_note
    )
    
    // 絵文字から新しいキーへのマッピング（後方互換性用）
    private val emojiToKeyMap: Map<String, String> = mapOf(
        "👀" to "watching",
        "💰" to "holding",
        "🤔" to "considering",
        "✅" to "sold",
        "⭐" to "star",
        "🔥" to "fire",
        "💎" to "diamond",
        "🚩" to "flag",
        "📈" to "chart",
        "🚀" to "rocket",
        "❤️" to "favorite",
        "💖" to "favorite",
        "⚡" to "bolt",
        "📊" to "all",
        "📁" to "folder",
        "📝" to "note"
    )
    
    // グループ選択用のアイコンリスト
    val selectableIcons: List<String> = listOf(
        "watching", "holding", "considering", "sold",
        "star", "fire", "diamond", "flag",
        "chart", "rocket", "favorite", "bolt"
    )
    
    /**
     * アイコンキーからリソースIDを取得
     * 絵文字が渡された場合は適切なアイコンにマッピング
     * 見つからない場合はデフォルトアイコンを返す
     */
    fun getIconResId(iconKey: String): Int {
        // まず直接マッピングを試みる
        iconMap[iconKey]?.let { return it }
        
        // 絵文字の場合は変換を試みる
        emojiToKeyMap[iconKey]?.let { newKey ->
            iconMap[newKey]?.let { return it }
        }
        
        // デフォルトアイコン（目のアイコン）を返す
        return R.drawable.ic_watching
    }
    
    /**
     * 絵文字を新しいキーに変換
     * 変換できない場合は元の値を返す
     */
    fun convertEmojiToKey(iconOrEmoji: String): String {
        // 既にキーの場合はそのまま返す
        if (iconMap.containsKey(iconOrEmoji)) {
            return iconOrEmoji
        }
        
        // 絵文字の場合は変換
        return emojiToKeyMap[iconOrEmoji] ?: "watching"
    }
    
    /**
     * アイコンキーの表示名を取得（日本語）
     */
    fun getIconDisplayName(iconKey: String): String {
        return when (iconKey) {
            "watching" -> "監視中"
            "holding" -> "保有中"
            "considering" -> "検討中"
            "sold" -> "売却済"
            "star" -> "スター"
            "fire" -> "注目"
            "diamond" -> "ダイヤ"
            "flag" -> "フラグ"
            "chart" -> "チャート"
            "rocket" -> "急騰"
            "favorite" -> "お気に入り"
            "bolt" -> "速報"
            "all" -> "すべて"
            else -> iconKey
        }
    }
}
