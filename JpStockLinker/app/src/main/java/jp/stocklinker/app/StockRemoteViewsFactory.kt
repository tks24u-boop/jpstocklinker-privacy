package jp.stocklinker.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var stockList: List<StockItem> = listOf()
    private var selectedCode: String? = null
    private val dateFormat = SimpleDateFormat("MM/dd", Locale.JAPAN)

    override fun onCreate() {
        // 初期化処理（必要であれば）
    }

    override fun onDataSetChanged() {
        selectedCode = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(AppConstants.KEY_WIDGET_SELECTED_CODE, null)
        Log.d("StockWidget", "Factory: onDataSetChanged, selectedCode=$selectedCode")
        loadStockData()
    }

    override fun onDestroy() {
        stockList = listOf()
    }

    override fun getCount(): Int {
        return stockList.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= stockList.size) {
            return RemoteViews(context.packageName, R.layout.widget_stock_item)
        }

        val stock = stockList[position]
        val views = RemoteViews(context.packageName, R.layout.widget_stock_item)

        val isSelected = selectedCode != null && selectedCode == stock.code
        Log.d("StockWidget", "getViewAt[$position]: ${stock.code}, selected=$selectedCode, isSelected=$isSelected")
        
        val bgRes = if (isSelected) R.drawable.bg_widget_item_selected else R.drawable.bg_widget_item_default
        views.setInt(R.id.widget_item_container, "setBackgroundResource", bgRes)

        // データのセット
        views.setTextViewText(R.id.widget_item_code, stock.code)
        views.setTextViewText(R.id.widget_item_name, stock.name)
        
        // 日付の表示（最後に検索した日時）
        val dateText = dateFormat.format(Date(stock.lastSearchedAt))
        views.setTextViewText(R.id.widget_item_date, dateText)
        
        // 価格の表示
        val priceText = stock.lastViewedPrice ?: "-"
        views.setTextViewText(R.id.widget_item_price, priceText)

        // 行全体のクリックイベント（銘柄選択）
        // URIに銘柄コードを含める
        val selectIntent = Intent().apply {
            data = android.net.Uri.parse("stock://select/${stock.code}")
        }
        views.setOnClickFillInIntent(R.id.widget_item_container, selectIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    private fun loadStockData() {
        try {
            val json = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(AppConstants.KEY_STOCK_LIST, null)
            
            if (json != null) {
                val type = object : TypeToken<ArrayList<StockItem>>() {}.type
                val allStocks: List<StockItem> = Gson().fromJson(json, type)
                // お気に入りのみフィルタリング（件数制限なし）
                stockList = allStocks.filter { it.isFavorite }
                Log.d("StockWidget", "Factory: Loaded ${stockList.size} favorites")
            } else {
                stockList = emptyList()
            }
        } catch (e: Exception) {
            Log.e("StockWidget", "Error loading data in factory", e)
            stockList = emptyList()
        }
    }
}
