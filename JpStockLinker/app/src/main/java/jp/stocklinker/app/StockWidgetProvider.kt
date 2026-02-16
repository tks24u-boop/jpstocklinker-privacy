package jp.stocklinker.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.concurrent.thread

/**
 * 株式リンカー ウィジェットプロバイダー
 * お気に入り銘柄をホーム画面に表示
 *
 * 注: notifyAppWidgetViewDataChanged / setRemoteAdapter は API 35 で非推奨だが、
 * RemoteCollectionItems 等への移行は大規模なため現状維持
 */
@Suppress("DEPRECATION")
class StockWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (context == null || intent == null) return
        
        Log.d("StockWidget", "onReceive: action=${intent.action}, data=${intent.data}")
        
        when (intent.action) {
            ACTION_SELECT_STOCK -> {
                // URIから銘柄コードを取得（例: stock://select/7203）
                val selectedCode = intent.data?.lastPathSegment
                Log.d("StockWidget", "SELECT_STOCK received: code=$selectedCode")
                
                if (!selectedCode.isNullOrEmpty()) {
                    // 同期的に保存（commit）してから更新
                    val saved = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putString(AppConstants.KEY_WIDGET_SELECTED_CODE, selectedCode)
                        .commit()
                    
                    Log.d("StockWidget", "Selected code saved: $saved")
                    
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        android.content.ComponentName(context, StockWidgetProvider::class.java)
                    )
                    
                    // リストのみを更新（ハイライト反映）- ちらつき防止
                    manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list_view)
                }
            }
            ACTION_OPEN_LINK -> {
                // リンクを開きつつ、バックグラウンドで株価を取得
                val urlTemplate = intent.getStringExtra(EXTRA_URL) ?: ""
                
                // 最新の選択銘柄をSharedPreferencesから取得
                val stockList = loadStockData(context)
                val favoriteStocks = stockList.filter { it.isFavorite }
                val code = getSelectedCode(context, favoriteStocks)
                
                Log.d("StockWidget", "OPEN_LINK received: code=$code, urlTemplate=$urlTemplate")
                
                // URLの{code}を置換
                val url = if (!code.isNullOrEmpty()) {
                    urlTemplate.replace("{code}", code)
                } else {
                    "https://finance.yahoo.co.jp/"
                }
                
                // ブラウザを開く
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                
                // Yahoo!ファイナンスの場合はバックグラウンドで株価を取得
                if (urlTemplate.contains("finance.yahoo.co.jp") && !code.isNullOrEmpty()) {
                    fetchAndSaveStockPrice(context, code)
                }
            }
            ACTION_TWITTER_SEARCH -> {
                // X（Twitter）で銘柄名を検索（最適化された検索クエリ）
                val stockList = loadStockData(context)
                val favoriteStocks = stockList.filter { it.isFavorite }
                val code = getSelectedCode(context, favoriteStocks)
                
                // 銘柄名とコードを取得
                val stockName = stockList.find { it.code == code }?.name ?: ""
                val stockCode = code ?: ""
                
                val searchQuery = if (stockName.isNotEmpty() || stockCode.isNotEmpty()) {
                    // より多くの検索結果を得るための最適化されたクエリ
                    // 1. 会社名と銘柄コードの両方をOR検索で含める
                    // 2. 複数のキーワード（株、投資、株価、ハッシュタグ）をOR検索で組み合わせ
                    // 3. lang:ja で日本語ツイートに絞る
                    // 4. -filter:replies でリプライを除外し本文投稿に集中
                    val namePart = if (stockName.isNotEmpty() && stockCode.isNotEmpty()) {
                        "($stockName OR $stockCode)"
                    } else if (stockName.isNotEmpty()) {
                        stockName
                    } else if (stockCode.isNotEmpty()) {
                        stockCode
                    } else {
                        ""
                    }
                    
                    val keywordPart = "(株 OR 投資 OR 株価 OR #株 OR #投資 OR #日本株)"
                    
                    val optimizedQuery = "$namePart $keywordPart lang:ja -filter:replies"
                    java.net.URLEncoder.encode(optimizedQuery, "UTF-8")
                } else {
                    ""
                }
                
                Log.d("StockWidget", "TWITTER_SEARCH: name=$stockName, code=$stockCode, query=$searchQuery")
                
                val url = if (searchQuery.isNotEmpty()) {
                    "https://x.com/search?q=$searchQuery&src=typed_query"
                } else {
                    "https://x.com/"
                }
                
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
            }
        }
    }



    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("StockWidget", "onUpdate called with ${appWidgetIds.size} widget(s)")
        try {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
            // リストデータの更新を通知
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
        } catch (e: Exception) {
            Log.e("StockWidget", "Error in onUpdate", e)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d("StockWidget", "onEnabled: First widget added")
        // 最初のウィジェットが追加された時の処理
    }

    override fun onDisabled(context: Context) {
        Log.d("StockWidget", "onDisabled: Last widget removed")
        // 最後のウィジェットが削除された時の処理
    }

    companion object {
        private const val ACTION_SELECT_STOCK = "jp.stocklinker.app.ACTION_SELECT_STOCK"
        private const val ACTION_OPEN_LINK = "jp.stocklinker.app.ACTION_OPEN_LINK"
        private const val ACTION_TWITTER_SEARCH = "jp.stocklinker.app.ACTION_TWITTER_SEARCH"
        private const val EXTRA_SELECTED_CODE = "selected_code"
        private const val EXTRA_URL = "url"
        private const val EXTRA_CODE = "code"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                Log.d("StockWidget", "updateAppWidget called for widget $appWidgetId")
                
                // データを読み込む
                val stockList = loadStockData(context)
                val favoriteStocks = stockList.filter { it.isFavorite }
                
                Log.d("StockWidget", "Loaded ${stockList.size} stocks, ${favoriteStocks.size} favorites")

                // メインレイアウトの読み込みを試みる
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_stock_list)
                    Log.d("StockWidget", "RemoteViews created with widget_stock_list")

                    // タイトル設定
                    views.setTextViewText(R.id.widget_title, "お気に入り銘柄 (${favoriteStocks.size})")

                    if (favoriteStocks.isEmpty()) {
                        // お気に入りがない場合
                        views.setTextViewText(R.id.widget_empty_text, "お気に入り銘柄がありません\nアプリで銘柄をお気に入りに登録してください")
                        views.setViewVisibility(R.id.widget_empty_text, android.view.View.VISIBLE)
                        views.setViewVisibility(R.id.widget_list_view, android.view.View.GONE)
                    } else {
                        // お気に入りがある場合
                        views.setViewVisibility(R.id.widget_empty_text, android.view.View.GONE)
                        views.setViewVisibility(R.id.widget_list_view, android.view.View.VISIBLE)

                        // リストビューのアダプターを設定
                        val intent = Intent(context, StockWidgetService::class.java).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                        }
                        views.setRemoteAdapter(R.id.widget_list_view, intent)
                        views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_text)

                    // リスト項目のクリックテンプレート設定（選択用ブロードキャスト）
                    val templateIntent = Intent(context, StockWidgetProvider::class.java).apply {
                        action = ACTION_SELECT_STOCK
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        // dataはFillInIntentで設定するので、ここでは設定しない
                    }
                    val templatePendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        templateIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    views.setPendingIntentTemplate(R.id.widget_list_view, templatePendingIntent)
                    }

                    // タイトルクリック・更新ボタンのイベント設定（共通）
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val mainPendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_title, mainPendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_search_bar, mainPendingIntent)

                    // ===== 1行目：選択銘柄リンク =====
                    val yahooQuick = createQuickLinkPendingIntent(
                        context,
                        1001,
                        "https://finance.yahoo.co.jp/quote/{code}.T"
                    )
                    views.setOnClickPendingIntent(R.id.widget_quick_yahoo, yahooQuick)

                    val kabutanQuick = createQuickLinkPendingIntent(
                        context,
                        1002,
                        "https://kabutan.jp/stock/?code={code}"
                    )
                    views.setOnClickPendingIntent(R.id.widget_quick_kabutan, kabutanQuick)

                    // X（Twitter）検索 - 銘柄名で検索
                    val twitterQuick = createTwitterSearchPendingIntent(context, 1003)
                    views.setOnClickPendingIntent(R.id.widget_quick_twitter, twitterQuick)

                    // TradingView（チャート）- 銘柄別
                    val chartQuick = createQuickLinkPendingIntent(
                        context,
                        1004,
                        "https://jp.tradingview.com/chart/?symbol=TSE:{code}"
                    )
                    views.setOnClickPendingIntent(R.id.widget_quick_chart, chartQuick)

                    // ===== 2行目：固定リンク =====
                    
                    // 決算カレンダー（銘柄コードに依存しない固定URL）
                    val calendarIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kabuyoho.jp/sp/calender"))
                    val calendarPendingIntent = PendingIntent.getActivity(
                        context,
                        1005,
                        calendarIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_quick_calendar, calendarPendingIntent)

                    // 寄与度ランキング（固定URL）
                    val contributionIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kabutan.jp/warning/?mode=8_1"))
                    val contributionPendingIntent = PendingIntent.getActivity(
                        context,
                        1006,
                        contributionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_quick_contribution, contributionPendingIntent)

                    // ヒートマップ（固定URL）
                    val heatmapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.moomoo.com/ja/quote/jp/heatmap"))
                    val heatmapPendingIntent = PendingIntent.getActivity(
                        context,
                        1007,
                        heatmapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_quick_heatmap, heatmapPendingIntent)

                    // 日経先物（固定URL）
                    val nikkei225Intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://nikkei225jp.com/"))
                    val nikkei225PendingIntent = PendingIntent.getActivity(
                        context,
                        1008,
                        nikkei225Intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_quick_nikkei225, nikkei225PendingIntent)

                    val refreshIntent = Intent(context, StockWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                    }
                    val refreshPendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)

                    // 更新反映
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    Log.d("StockWidget", "Widget updated successfully")

                } catch (e: Exception) {
                    Log.e("StockWidget", "Failed to create/update widget with main layout", e)
                    // フォールバック：シンプルレイアウトでエラー表示
                    try {
                        val errorViews = RemoteViews(context.packageName, R.layout.widget_stock_list_simple)
                        errorViews.setTextViewText(R.id.widget_title, "お気に入り銘柄")
                        // シンプルなエラーメッセージ
                        errorViews.setTextViewText(R.id.widget_empty_text, "表示エラーが発生しました\n(タップして更新)")
                        
                        // タップで更新を試みるように設定
                        val refreshIntent = Intent(context, StockWidgetProvider::class.java).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                        }
                        val refreshPendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        errorViews.setOnClickPendingIntent(R.id.widget_empty_text, refreshPendingIntent)
                        
                        appWidgetManager.updateAppWidget(appWidgetId, errorViews)
                    } catch (e2: Exception) {
                        Log.e("StockWidget", "Critical error: Failed to show fallback widget", e2)
                    }
                }
            } catch (e: Exception) {
                // outer catch is not needed as we handle it inside loop per widget, but keeping structure clean
                Log.e("StockWidget", "Unexpected error in update loop", e)
            }
        }

        private fun loadStockData(context: Context): List<StockItem> {
            val json = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(AppConstants.KEY_STOCK_LIST, null)
            return if (json != null) {
                val type = object : TypeToken<ArrayList<StockItem>>() {}.type
                Gson().fromJson(json, type)
            } else {
                emptyList()
            }
        }

        private fun getSelectedCode(context: Context, favoriteStocks: List<StockItem>): String? {
            val saved = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(AppConstants.KEY_WIDGET_SELECTED_CODE, null)
            if (!saved.isNullOrEmpty()) return saved
            return favoriteStocks.firstOrNull()?.code
        }

        private fun createQuickLinkPendingIntent(
            context: Context,
            requestCode: Int,
            templateUrl: String
        ): PendingIntent {
            // すべてのリンクをACTION_OPEN_LINK経由で処理（タップ時に最新の銘柄コードを取得）
            val intent = Intent(context, StockWidgetProvider::class.java).apply {
                action = ACTION_OPEN_LINK
                putExtra(EXTRA_URL, templateUrl)  // テンプレートURLを保存（{code}含む）
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }
        
        /**
         * X（Twitter）で銘柄検索するPendingIntent
         */
        private fun createTwitterSearchPendingIntent(
            context: Context,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, StockWidgetProvider::class.java).apply {
                action = ACTION_TWITTER_SEARCH
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }
        
        /**
         * バックグラウンドで株価を取得して保存
         */
        private fun fetchAndSaveStockPrice(context: Context, code: String) {
            thread {
                try {
                    val url = java.net.URL("https://finance.yahoo.co.jp/quote/${code}.T")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    
                    val html = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    
                    // 株価を抽出
                    val price = extractPriceFromHtml(html)
                    
                    if (price != null) {
                        // データを更新して保存
                        val stockList = loadStockData(context).toMutableList()
                        stockList.find { it.code == code }?.let { stock ->
                            stock.lastViewedPrice = price
                            stock.lastSearchedAt = System.currentTimeMillis()
                            
                            // SharedPreferencesに保存
                            context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
                                .putString(AppConstants.KEY_STOCK_LIST, Gson().toJson(stockList))
                                .apply()
                            
                            Log.d("StockWidget", "Price saved for $code: $price")
                            
                            // ウィジェットを更新
                            val manager = AppWidgetManager.getInstance(context)
                            val ids = manager.getAppWidgetIds(
                                android.content.ComponentName(context, StockWidgetProvider::class.java)
                            )
                            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list_view)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("StockWidget", "Failed to fetch price for $code: ${e.message}")
                }
            }
        }
        
        /**
         * HTMLから株価を抽出
         */
        private fun extractPriceFromHtml(html: String): String? {
            return try {
                val patterns = listOf(
                    """class="[^"]*StyledNumber[^"]*"[^>]*>([0-9,]+(?:\.[0-9]+)?)</""".toRegex(),
                    """<span[^>]*>([0-9,]+(?:\.[0-9]+)?)</span>\s*<span[^>]*class="[^"]*change""".toRegex(),
                    """現在値[^0-9]*([0-9,]+(?:\.[0-9]+)?)""".toRegex()
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        val priceStr = match.groupValues[1]
                        if (priceStr.replace(",", "").toDoubleOrNull() != null) {
                            return "¥$priceStr"
                        }
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}
