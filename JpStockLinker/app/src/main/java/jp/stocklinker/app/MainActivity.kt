package jp.stocklinker.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.stocklinker.app.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.OnBackPressedCallback
import android.appwidget.AppWidgetManager

// ========== 定数定義（v7.3追加）==========
object AppConstants {
    const val PREFS_NAME = "StockPrefs"
    const val KEY_STOCK_LIST = "StockListV60"
    const val KEY_GROUP_LIST = "GroupListV1"
    const val KEY_DISCLOSURE_EXPANDED = "disclosure_expanded"
    const val KEY_HEADER_EXPANDED = "header_expanded"
    const val KEY_WIDGET_SELECTED_CODE = "widget_selected_code"
    const val TABLET_WIDTH_DP = 600
    const val ANIMATION_DURATION_MS = 300L
    const val SUGGEST_MIN_QUERY_LENGTH = 2
    const val SUGGEST_MAX_RESULTS = 10
}

// 保存データ用（v6.0: groupId追加、v7.3.1: lastViewedPrice追加）
data class StockItem(
    val code: String,
    val name: String,
    var memo: String = "",
    var isFavorite: Boolean = false,
    var lastSearchedAt: Long = System.currentTimeMillis(),
    var sector: String = "",
    var themes: List<String> = listOf(),
    var groupId: String? = null,
    var lastViewedPrice: String? = null  // 最後に閲覧時の価格（表示用文字列）
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val masterStockList = ArrayList<StockItem>()
    private val displayStockList = ArrayList<StockItem>()
    private lateinit var stockAdapter: StockAdapter
    
    // サジェスト用
    private val suggestList = ArrayList<MasterStockItem>()
    private lateinit var suggestAdapter: SuggestAdapter
    private var selectedMasterItem: MasterStockItem? = null
    
    // グループ関連
    private val groupList = ArrayList<Group>()
    private var selectedGroupId: String? = null
    
    // ========== 折りたたみ対応（v6.5追加）==========
    private var isDualPane = false
    private val economicNewsList = ArrayList<NewsItem>()
    private val disclosureNewsList = ArrayList<NewsItem>()
    private lateinit var economicNewsAdapter: NewsAdapter
    private lateinit var disclosureNewsAdapter: NewsAdapter
    
    // ========== トグル機能（v7.3リファクタリング）==========
    private var isDisclosureExpanded = true  // 適時開示トグル
    private var isHeaderExpanded = true      // ヘッダートグル（v7.3追加）
    private var isDockExpanded = false       // リンクドック拡張状態
    // ========== トグル機能ここまで ==========
    
    private var currentSelectedCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // マスターデータ読み込み（バックグラウンドで実行）
        Thread {
            StockMasterRepository.load(this)
        }.start()
        
        // ========== 折りたたみ対応（v6.5追加）==========
        setupFoldableLayout()
        setupSwipeNavigation()  // v6.7追加
        // ========== 折りたたみ対応ここまで ==========
        
        // ========== ヘッダートグル（v7.3追加）==========
        loadHeaderToggleState()
        setupHeaderToggle()
        // ========== ヘッダートグルここまで ==========

        // データ読み込み & 初期ソート
        loadData()
        if (masterStockList.isEmpty()) addDemoData()
        
        // グループ読み込み
        loadGroups()
        if (groupList.isEmpty()) addDefaultGroups()
        
        sortAndDisplay()

        // ウィジェットからの起動インテント処理
        handleWidgetIntent(intent)

        // Fold最適化: 画面幅400dpを基準に列数を自動計算
        val spanCount = calculateNoOfColumns(this, 400f)
        binding.recyclerView.layoutManager = GridLayoutManager(this, spanCount)

        // メインリストアダプター設定
        stockAdapter = StockAdapter(
            displayStockList,
            onItemClick = { item -> onStockItemClicked(item) },
            onDeleteClick = { item -> showDeleteDialog(item) },
            onFavoriteClick = { item -> toggleFavorite(item) },
            onMemoClick = { item -> showMemoDialog(item) },
            onMoveClick = { item -> showMoveToGroupDialog(item) },
            getGroupInfo = { groupId -> groupList.find { it.id == groupId } }
        )
        binding.recyclerView.adapter = stockAdapter
        updateCount()

        // サジェストRecyclerView設定
        binding.rvSuggest.layoutManager = LinearLayoutManager(this)
        suggestAdapter = SuggestAdapter(suggestList) { item ->
            onSuggestItemClicked(item)
        }
        binding.rvSuggest.adapter = suggestAdapter

        // グループタブ設定
        setupGroupTabs()
        
        // グループ追加ボタン
        binding.chipAddGroup.setOnClickListener {
            showAddGroupDialog()
        }
        
        // 「すべて」タブ
        binding.chipAll.setOnClickListener {
            selectGroup(null)
        }

        // 選択解除ボタン
        binding.btnClearSelected.setOnClickListener {
            clearSelection()
        }

        // 検索リスナー
        binding.etSearchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                filterList(query)
                updateSuggestList(query)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // ボタンリスナー
        binding.btnSave.setOnClickListener { showAddDialog() }

        // === リンクボタン ===
        binding.btnYahoo.setOnClickListener { launchUrl("https://finance.yahoo.co.jp/quote/{code}.T", true) }
        binding.btnKabutan.setOnClickListener { launchUrl("https://kabutan.jp/stock/?code={code}", true) }
        binding.btnShikiho.setOnClickListener { launchUrl("https://shikiho.toyokeizai.net/stocks/{code}", false) }
        binding.btnKarauri.setOnClickListener { launchUrl("https://karauri.net/{code}/", false) }
        binding.btnAshiato.setOnClickListener {
            launchUrl("https://japan-kabuka.com/gif?id={code}", false)
        }
        binding.btnTradingView.setOnClickListener { 
            launchUrl("https://jp.tradingview.com/symbols/TSE-{code}/", false) 
        }
        binding.btnMinkabu.setOnClickListener { 
            launchUrl("https://minkabu.jp/stock/{code}", true) 
        }
        binding.btnBuffett.setOnClickListener { 
            launchUrl("https://www.buffett-code.com/company/{code}/", false) 
        }
        binding.btnNikkei.setOnClickListener { 
            launchUrl("https://www.nikkei.com/nkd/company/?scode={code}", true) 
        }
        binding.btnKabudragon.setOnClickListener { 
            launchUrl("https://www.kabudragon.com/stock/{code}/", false) 
        }
        binding.btnIrbank.setOnClickListener { 
            launchUrl("https://irbank.net/search/{code}", false) 
        }
        
        // === 市場全体リンク（銘柄コード不要） ===
        binding.btnWorldStock.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://nikkei225jp.com/"))
            startActivity(intent)
        }
        
        binding.btnNikkei225.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://nikkei225jp.com/"))
            startActivity(intent)
        }
        
        binding.btnKabuyoho.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kabuyoho.jp/sp/calender"))
            startActivity(intent)
        }
        
        binding.btnKabutanUp.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kabutan.jp/warning/?mode=2_1"))
            startActivity(intent)
        }
        
        binding.btnKabutanDown.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kabutan.jp/warning/?mode=2_2&market=1&dispmode=normal"))
            startActivity(intent)
        }
        
        binding.btnHeatmap.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.moomoo.com/ja/quote/jp/heatmap"))
            startActivity(intent)
        }
        
        // === 個別銘柄リンク（日証金） ===
        binding.btnNisshokin.setOnClickListener {
            launchUrl("https://www.taisyaku.jp/app/stock/detail/{code}-01#search-result", true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent) {
        val clickAction = intent.getStringExtra("click_action")
        val selectedCode = intent.getStringExtra("selected_code")
        val yahooUrl = intent.getStringExtra("yahoo_url")

        if (clickAction == "open_yahoo" && !yahooUrl.isNullOrEmpty()) {
            // Yahoo!ファイナンスを開く
            // アプリが起動してしまうので、ブラウザへ飛ばす
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(yahooUrl))
            startActivity(browserIntent)
            // アプリは裏へ回すために終了させる？いや、ユーザーは戻ってくるかもしれないのでそのまま
        } else if (clickAction == "open_app" || !selectedCode.isNullOrEmpty()) {
            // 該当銘柄を選択状態にする
            if (!selectedCode.isNullOrEmpty()) {
                val stockItem = masterStockList.find { it.code == selectedCode }
                if (stockItem != null) {
                    binding.recyclerView.post {
                        onStockItemClicked(stockItem)
                    }
                }
            }
        }
    }

    // === グループ関連メソッド ===

    private fun addDefaultGroups() {
        groupList.add(Group(id = "watching", name = "監視中", icon = "👀", color = "#4FC3F7", order = 0))
        groupList.add(Group(id = "holding", name = "保有中", icon = "💰", color = "#81C784", order = 1))
        groupList.add(Group(id = "considering", name = "検討中", icon = "🤔", color = "#FFB74D", order = 2))
        groupList.add(Group(id = "sold", name = "売却済", icon = "✅", color = "#9E9E9E", order = 3))
        saveGroups()
    }

    private fun setupGroupTabs() {
        binding.layoutDynamicGroups.removeAllViews()
        
        for (group in groupList.sortedBy { it.order }) {
            val chip = Chip(this).apply {
                text = group.name
                chipIconSize = dpToPx(20f)
                setChipIconResource(IconManager.getIconResId(group.icon))
                isChipIconVisible = true
                textSize = 14f
                isCheckable = true
                isChecked = (selectedGroupId == group.id)
                
                val groupColor = try { Color.parseColor(group.color) } catch (e: Exception) { Color.GRAY }
                chipBackgroundColor = ColorStateList.valueOf(
                    if (isChecked) groupColor else Color.argb(50, Color.red(groupColor), Color.green(groupColor), Color.blue(groupColor))
                )
                chipStrokeColor = ColorStateList.valueOf(groupColor)
                chipStrokeWidth = if (isChecked) 2f else 1f
                // chipCornerRadiusはレイアウトXMLで設定済み
                
                setOnClickListener {
                    selectGroup(group.id)
                }
                
                setOnLongClickListener {
                    showEditGroupDialog(group)
                    true
                }
            }
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = dpToPx(8f).toInt()
            chip.layoutParams = params
            
            binding.layoutDynamicGroups.addView(chip)
        }
    }

    private fun selectGroup(groupId: String?) {
        selectedGroupId = groupId
        
        // タブの選択状態を更新
        binding.chipAll.isChecked = (groupId == null)
        setupGroupTabs()
        
        // リストをフィルタリング
        filterList(binding.etSearchInput.text.toString())
        
        // ヘッダーテキスト更新
        val headerText = if (groupId == null) {
            "マイリスト"
        } else {
            val group = groupList.find { it.id == groupId }
            group?.name ?: "マイリスト"
        }
        binding.tvListHeader.text = headerText
    }

    private fun showAddGroupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_group_edit, null)
        val etName = dialogView.findViewById<EditText>(R.id.etGroupName)
        
        var selectedIcon = "watching"
        var selectedColor = "#4FC3F7"
        
        // アイコンピッカー設定（Vector Drawable）
        val gridIcons = dialogView.findViewById<GridLayout>(R.id.gridIcons)
        gridIcons.removeAllViews()
        
        for (iconKey in IconManager.selectableIcons) {
            val iconView = ImageView(this).apply {
                val size = dpToPx(44f).toInt()
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt())
                }
                setImageResource(IconManager.getIconResId(iconKey))
                imageTintList = ColorStateList.valueOf(Color.parseColor(selectedColor))
                setBackgroundResource(R.drawable.bg_input)
                setPadding(dpToPx(8f).toInt(), dpToPx(8f).toInt(), dpToPx(8f).toInt(), dpToPx(8f).toInt())
                alpha = if (iconKey == selectedIcon) 1.0f else 0.5f
                
                setOnClickListener {
                    selectedIcon = iconKey
                    for (j in 0 until gridIcons.childCount) {
                        gridIcons.getChildAt(j).alpha = 0.5f
                    }
                    this.alpha = 1.0f
                }
            }
            gridIcons.addView(iconView)
        }
        
        // カラーピッカー設定
        val colors = listOf("#4FC3F7", "#81C784", "#FFB74D", "#EF5350", "#AB47BC", "#5C6BC0", "#26A69A", "#FFA726")
        val layoutColorPicker = dialogView.findViewById<LinearLayout>(R.id.layoutColorPicker)
        for (color in colors) {
            val view = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(48f).toInt(), dpToPx(48f).toInt()).apply {
                    marginEnd = dpToPx(12f).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(color))
                }
                setOnClickListener {
                    selectedColor = color
                    for (j in 0 until layoutColorPicker.childCount) {
                        (layoutColorPicker.getChildAt(j).background as? GradientDrawable)?.setStroke(0, 0)
                    }
                    (this.background as? GradientDrawable)?.setStroke(dpToPx(4f).toInt(), Color.WHITE)
                }
            }
            layoutColorPicker.addView(view)
        }
        
        AlertDialog.Builder(this)
            .setTitle("グループを追加")
            .setView(dialogView)
            .setPositiveButton("追加") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newGroup = Group(
                        name = name,
                        icon = selectedIcon,
                        color = selectedColor,
                        order = groupList.size
                    )
                    groupList.add(newGroup)
                    saveGroups()
                    setupGroupTabs()
                    Toast.makeText(this, "グループを追加しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showEditGroupDialog(group: Group) {
        val options = arrayOf("名前を変更", "グループを削除")
        
        AlertDialog.Builder(this)
            .setTitle(group.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(group)
                    1 -> showDeleteGroupDialog(group)
                }
            }
            .show()
    }

    private fun showRenameGroupDialog(group: Group) {
        val editText = EditText(this).apply {
            setText(group.name)
            setPadding(dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt())
        }
        
        AlertDialog.Builder(this)
            .setTitle("グループ名を変更")
            .setView(editText)
            .setPositiveButton("変更") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    group.name = newName
                    saveGroups()
                    setupGroupTabs()
                    if (selectedGroupId == group.id) {
                        binding.tvListHeader.text = group.name
                    }
                    Toast.makeText(this, "名前を変更しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showDeleteGroupDialog(group: Group) {
        val stocksInGroup = masterStockList.count { it.groupId == group.id }
        
        AlertDialog.Builder(this)
            .setTitle("グループを削除")
            .setMessage("「${group.name}」を削除しますか？\n\nこのグループの銘柄（${stocksInGroup}件）は「未分類」に移動します。")
            .setPositiveButton("削除") { _, _ ->
                masterStockList.filter { it.groupId == group.id }.forEach { it.groupId = null }
                saveData()
                
                groupList.remove(group)
                saveGroups()
                
                if (selectedGroupId == group.id) {
                    selectGroup(null)
                }
                
                setupGroupTabs()
                Toast.makeText(this, "グループを削除しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showMoveToGroupDialog(item: StockItem) {
        val options = mutableListOf("未分類")
        options.addAll(groupList.map { it.name })
        
        val currentIndex = if (item.groupId == null) {
            0
        } else {
            groupList.indexOfFirst { it.id == item.groupId } + 1
        }
        
        AlertDialog.Builder(this)
            .setTitle("グループに移動")
            .setSingleChoiceItems(options.toTypedArray(), currentIndex) { dialog, which ->
                item.groupId = if (which == 0) null else groupList[which - 1].id
                saveData()
                filterList(binding.etSearchInput.text.toString())
                dialog.dismiss()
                Toast.makeText(this, "移動しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun saveGroups() {
        getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(AppConstants.KEY_GROUP_LIST, Gson().toJson(groupList))
            .apply()
    }

    private fun loadGroups() {
        val json = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(AppConstants.KEY_GROUP_LIST, null)
        if (json != null) {
            val type = object : TypeToken<ArrayList<Group>>() {}.type
            groupList.clear()
            val loadedGroups: ArrayList<Group> = Gson().fromJson(json, type)
            // 後方互換性: 絵文字を新しいキーに変換
            var needsSave = false
            for (group in loadedGroups) {
                val convertedIcon = IconManager.convertEmojiToKey(group.icon)
                if (convertedIcon != group.icon) {
                    group.icon = convertedIcon
                    needsSave = true
                }
            }
            groupList.addAll(loadedGroups)
            if (needsSave) {
                saveGroups()
            }
        }
    }

    // === サジェスト関連 ===

    private fun updateSuggestList(query: String) {
        if (query.length < 2) {
            suggestList.clear()
            suggestAdapter.notifyDataSetChanged()
            binding.rvSuggest.visibility = View.GONE
            return
        }

        val results = StockMasterRepository.search(query, 10)
        suggestList.clear()
        suggestList.addAll(results)
        suggestAdapter.notifyDataSetChanged()
        binding.rvSuggest.visibility = if (results.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun onSuggestItemClicked(item: MasterStockItem) {
        selectedMasterItem = item
        currentSelectedCode = item.code

        binding.etSearchInput.setText("${item.code} ${item.name}")
        binding.etSearchInput.setSelection(binding.etSearchInput.length())

        suggestList.clear()
        suggestAdapter.notifyDataSetChanged()
        binding.rvSuggest.visibility = View.GONE

        binding.cardSelected.visibility = View.VISIBLE
        showLinkDockForSelection()
        binding.tvSelectedCode.text = item.code
        binding.tvSelectedName.text = item.name
        
        displayTags(binding.layoutTags, item.sector, item.themes)

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearchInput.windowToken, 0)
    }

    private fun clearSelection() {
        selectedMasterItem = null
        currentSelectedCode = ""
        binding.etSearchInput.text.clear()
        binding.cardSelected.visibility = View.GONE
        hideLinkDockForNoSelection()
        filterList("")
    }

    // === フィルタリング ===

    private fun filterList(query: String) {
        displayStockList.clear()
        
        // グループフィルタを適用
        val baseList = if (selectedGroupId == null) {
            masterStockList
        } else {
            masterStockList.filter { it.groupId == selectedGroupId }
        }
        
        if (query.isEmpty()) {
            displayStockList.addAll(baseList)
        } else {
            val qLower = query.lowercase()
            val qHira = katakanaToHiragana(query)
            val qKata = hiraganaToKatakana(query)

            for (item in baseList) {
                if (item.code.contains(query) ||
                    item.name.lowercase().contains(qLower) ||
                    item.name.contains(qHira) ||
                    item.name.contains(qKata) ||
                    hiraganaToKatakana(item.name).contains(qKata) ||
                    katakanaToHiragana(item.name).contains(qHira) ||
                    item.sector.lowercase().contains(qLower) ||
                    item.themes.any { it.lowercase().contains(qLower) }) {
                    displayStockList.add(item)
                }
            }
        }
        
        // ソート: お気に入り → 最近使った順
        displayStockList.sortWith(compareByDescending<StockItem> { it.isFavorite }.thenByDescending { it.lastSearchedAt })
        
        if (::stockAdapter.isInitialized) stockAdapter.notifyDataSetChanged()
        updateCount()
    }

    // === その他のメソッド ===

    private fun onStockItemClicked(item: StockItem) {
        currentSelectedCode = item.code
        item.lastSearchedAt = System.currentTimeMillis()
        saveData()
        
        binding.cardSelected.visibility = View.VISIBLE
        showLinkDockForSelection()
        binding.tvSelectedCode.text = item.code
        binding.tvSelectedName.text = item.name
        
        displayTags(binding.layoutTags, item.sector, item.themes)
        
        filterList(binding.etSearchInput.text.toString())
    }

    private fun displayTags(container: LinearLayout, sector: String, themes: List<String>) {
        container.removeAllViews()
        
        if (sector.isNotEmpty()) {
            val tvSector = TextView(this).apply {
                text = sector
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.tag_sector_text))
                setBackgroundResource(R.drawable.bg_tag_sector)
                setPadding(dpToPx(8f).toInt(), dpToPx(4f).toInt(), dpToPx(8f).toInt(), dpToPx(4f).toInt())
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = dpToPx(6f).toInt()
            tvSector.layoutParams = params
            container.addView(tvSector)
        }
        
        for (theme in themes.take(2)) {
            val tvTheme = TextView(this).apply {
                text = theme
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.tag_theme_text))
                setBackgroundResource(R.drawable.bg_tag_theme)
                setPadding(dpToPx(8f).toInt(), dpToPx(4f).toInt(), dpToPx(8f).toInt(), dpToPx(4f).toInt())
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = dpToPx(6f).toInt()
            tvTheme.layoutParams = params
            container.addView(tvTheme)
        }
    }

    private fun showAddDialog() {
        val searchText = binding.etSearchInput.text.toString().trim()
        
        if (selectedMasterItem != null) {
            val item = selectedMasterItem!!
            val existing = masterStockList.find { it.code == item.code }
            if (existing != null) {
                Toast.makeText(this, "「${item.code}」は既に登録されています", Toast.LENGTH_SHORT).show()
                return
            }
            
            val newItem = StockItem(
                code = item.code,
                name = item.name,
                sector = item.sector,
                themes = item.themes
            )
            masterStockList.add(newItem)
            saveData()
            clearSelection()
            sortAndDisplay()
            Toast.makeText(this, "「${item.code} ${item.name}」を登録しました", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_stock, null)
        val etCode = dialogView.findViewById<EditText>(R.id.etDialogCode)
        val etName = dialogView.findViewById<EditText>(R.id.etDialogName)
        
        if (searchText.matches(Regex("\\d{4}"))) {
            etCode.setText(searchText)
        } else if (searchText.isNotEmpty()) {
            etName.setText(searchText)
        }

        AlertDialog.Builder(this)
            .setTitle("📝 銘柄を登録")
            .setView(dialogView)
            .setPositiveButton("登録") { _, _ ->
                val code = etCode.text.toString().trim()
                val name = etName.text.toString().trim()
                if (code.isNotEmpty() && name.isNotEmpty()) {
                    if (masterStockList.any { it.code == code }) {
                        Toast.makeText(this, "「$code」は既に登録されています", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    masterStockList.add(StockItem(code, name))
                    saveData()
                    sortAndDisplay()
                    binding.etSearchInput.text.clear()
                    Toast.makeText(this, "「$code $name」を登録しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showDeleteDialog(item: StockItem) {
        AlertDialog.Builder(this)
            .setTitle("🗑️ 削除確認")
            .setMessage("「${item.code} ${item.name}」を削除しますか？")
            .setPositiveButton("削除") { _, _ ->
                masterStockList.remove(item)
                saveData()
                sortAndDisplay()
                Toast.makeText(this, "削除しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun toggleFavorite(item: StockItem) {
        item.isFavorite = !item.isFavorite
        saveData()
        sortAndDisplay()
    }

    private fun showMemoDialog(item: StockItem) {
        val editText = EditText(this).apply {
            setText(item.memo)
            hint = "目標株価、損切りラインなど"
            setPadding(dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt())
            minLines = 3
        }

        AlertDialog.Builder(this)
            .setTitle("📝 メモ編集")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                item.memo = editText.text.toString()
                saveData()
                stockAdapter.notifyDataSetChanged()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun launchUrl(urlTemplate: String, useCustomTabs: Boolean) {
        if (currentSelectedCode.isEmpty()) {
            Toast.makeText(this, "銘柄を選択してください", Toast.LENGTH_SHORT).show()
            return
        }
        val url = urlTemplate.replace("{code}", currentSelectedCode)
        
        // 株価取得をバックグラウンドで実行（リンクを開くのと並行）
        fetchAndSaveStockPrice(currentSelectedCode)
        
        if (useCustomTabs) {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(this, Uri.parse(url))
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    
    /**
     * Yahoo!ファイナンスから株価を取得して保存
     */
    private fun fetchAndSaveStockPrice(code: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://finance.yahoo.co.jp/quote/${code}.T")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                // 株価を抽出（Yahoo!ファイナンスのHTML構造に基づく）
                val price = extractPriceFromHtml(html)
                
                if (price != null) {
                    withContext(Dispatchers.Main) {
                        // 該当銘柄のlastViewedPriceを更新
                        masterStockList.find { it.code == code }?.let { stock ->
                            stock.lastViewedPrice = price
                            stock.lastSearchedAt = System.currentTimeMillis()
                            saveData()
                            stockAdapter.notifyDataSetChanged()
                        }
                    }
                }
            } catch (e: Exception) {
                // ネットワークエラー等は無視（リンクは正常に開く）
                android.util.Log.w("StockPrice", "Failed to fetch price for $code: ${e.message}")
            }
        }
    }
    
    /**
     * HTMLから株価を抽出
     */
    private fun extractPriceFromHtml(html: String): String? {
        return try {
            // Yahoo!ファイナンスの株価表示パターン（複数パターン対応）
            // パターン1: <span class="...price...">1,234</span>
            // パターン2: data-field="priceValue" などの属性
            
            // 現在値のセクションから数値を抽出
            val patterns = listOf(
                """class="[^"]*StyledNumber[^"]*"[^>]*>([0-9,]+(?:\.[0-9]+)?)</""".toRegex(),
                """<span[^>]*>([0-9,]+(?:\.[0-9]+)?)</span>\s*<span[^>]*class="[^"]*change""".toRegex(),
                """現在値[^0-9]*([0-9,]+(?:\.[0-9]+)?)""".toRegex()
            )
            
            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val priceStr = match.groupValues[1]
                    // 数値が有効かチェック（少なくとも1桁）
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

    private fun sortAndDisplay() {
        filterList(binding.etSearchInput.text.toString())
    }

    private fun updateCount() {
        val favCount = displayStockList.count { it.isFavorite }
        binding.tvCount.text = if (favCount > 0) {
            "${displayStockList.size}件 (⭐$favCount)"
        } else {
            "${displayStockList.size}件"
        }
    }

    private fun saveData() {
        getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(AppConstants.KEY_STOCK_LIST, Gson().toJson(masterStockList))
            .apply()
        
        // ウィジェットを更新
        updateWidgets()
    }
    
    private fun updateWidgets() {
        val widgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = widgetManager.getAppWidgetIds(
            android.content.ComponentName(this, StockWidgetProvider::class.java)
        )
        if (widgetIds.isNotEmpty()) {
            // すべてのウィジェットを更新
            for (widgetId in widgetIds) {
                StockWidgetProvider.updateAppWidget(this, widgetManager, widgetId)
            }
        }
    }

    private fun loadData() {
        val json = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(AppConstants.KEY_STOCK_LIST, null)
        if (json != null) {
            val type = object : TypeToken<ArrayList<StockItem>>() {}.type
            masterStockList.clear()
            masterStockList.addAll(Gson().fromJson(json, type))
        }
    }

    private fun addDemoData() {
        masterStockList.add(StockItem("3350", "メタプラネット", "ビットコイン投資で注目", true, groupId = "watching").apply { 
            themes = listOf("ビットコイン", "仮想通貨") 
        })
        masterStockList.add(StockItem("1570", "日経レバ", "日経平均2倍連動ETF", true, groupId = "holding").apply { 
            themes = listOf("ETF", "レバレッジ") 
        })
        masterStockList.add(StockItem("1357", "日経ダブルインバース", "日経平均-2倍連動", groupId = "watching").apply { 
            themes = listOf("ETF", "インバース") 
        })
        masterStockList.add(StockItem("7203", "トヨタ自動車", "", groupId = "holding").apply { 
            sector = "輸送用機器"
            themes = listOf("自動車", "EV関連") 
        })
        masterStockList.add(StockItem("9984", "ソフトバンクグループ", "").apply { 
            sector = "情報・通信業"
            themes = listOf("AI関連", "投資会社") 
        })
        masterStockList.add(StockItem("6920", "レーザーテック", "半導体検査装置", groupId = "watching").apply { 
            sector = "電気機器"
            themes = listOf("半導体", "半導体製造装置") 
        })
        masterStockList.add(StockItem("8035", "東京エレクトロン", "半導体製造装置", groupId = "watching").apply { 
            sector = "電気機器"
            themes = listOf("半導体", "半導体製造装置") 
        })
        saveData()
    }

    private fun calculateNoOfColumns(context: Context, columnWidthDp: Float): Int {
        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return ((screenWidthDp - 32) / columnWidthDp).toInt().coerceAtLeast(1)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    // ひらがな・カタカナ変換
    private fun hiraganaToKatakana(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            sb.append(if (c in '\u3041'..'\u3096') (c.code + 0x60).toChar() else c)
        }
        return sb.toString()
    }

    private fun katakanaToHiragana(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            sb.append(if (c in '\u30A1'..'\u30F6') (c.code - 0x60).toChar() else c)
        }
        return sb.toString()
    }
    
    // ========== 折りたたみ対応メソッド（v6.5追加）==========
    
    /**
     * 折りたたみレイアウトの初期化
     * 既存機能には影響しない
     */
    private fun setupFoldableLayout() {
        try {
            // ニュースアダプターの初期化
            economicNewsAdapter = NewsAdapter(economicNewsList)
            disclosureNewsAdapter = NewsAdapter(disclosureNewsList)
            
            // 右ペインのRecyclerViewを設定（ViewBinding経由）
            val newsBinding = binding.includeNews
            newsBinding.rvEconomicNews.layoutManager = LinearLayoutManager(this@MainActivity)
            newsBinding.rvEconomicNews.adapter = economicNewsAdapter
            
            newsBinding.rvTimelyDisclosure.layoutManager = LinearLayoutManager(this@MainActivity)
            newsBinding.rvTimelyDisclosure.adapter = disclosureNewsAdapter
            
            // 更新ボタンのリスナー
            newsBinding.btnRefreshNews.setOnClickListener {
                refreshNews()
            }
            newsBinding.btnRefreshDisclosure.setOnClickListener {
                refreshDisclosure()
            }
            
            // ========== 適時開示トグル（v6.6追加）==========
            loadDisclosureToggleState()
            setupDisclosureToggle(newsBinding)
            // ========== 適時開示トグルここまで ==========
            
            // ========== スワイプナビゲーション（v6.7追加）==========
            // 戻るボタンの処理
            newsBinding.btnBackToMain.setOnClickListener {
                navigateBackToMain()
            }
            // ========== スワイプナビゲーションここまで ==========
            
            // 折りたたみ状態の監視
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    WindowInfoTracker.getOrCreate(this@MainActivity)
                        .windowLayoutInfo(this@MainActivity)
                        .collect { layoutInfo ->
                            val foldingFeature = layoutInfo.displayFeatures
                                .filterIsInstance<FoldingFeature>()
                                .firstOrNull()
                            
                            // 開いた状態（FLAT）かどうかを判定
                            val isFlat = foldingFeature?.state == FoldingFeature.State.FLAT
                            val isLargeScreen = resources.configuration.screenWidthDp >= 600
                            
                            isDualPane = isFlat || isLargeScreen
                            
                            // 開いた状態ならニュースを取得
                            if (isDualPane) {
                                refreshNews()
                                refreshDisclosure()
                            }
                        }
                }
            }
            
            // 初回ニュース取得（大画面の場合）
            if (resources.configuration.screenWidthDp >= 600) {
                refreshNews()
                refreshDisclosure()
            }
            
        } catch (e: Exception) {
            // エラーが発生しても既存機能に影響しない
            e.printStackTrace()
        }
    }
    
    /**
     * 経済ニュースを更新
     */
    private fun refreshNews() {
        lifecycleScope.launch {
            try {
                val news = NewsRepository.fetchEconomicNews(15)
                economicNewsList.clear()
                economicNewsList.addAll(news)
                economicNewsAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 適時開示を更新
     */
    private fun refreshDisclosure() {
        lifecycleScope.launch {
            try {
                val disclosure = NewsRepository.fetchTimelyDisclosure(15)
                disclosureNewsList.clear()
                disclosureNewsList.addAll(disclosure)
                disclosureNewsAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    // ========== 折りたたみ対応ここまで ==========
    
    // ========== 適時開示トグル機能（v6.6追加）==========
    
    /**
     * 適時開示セクションのトグル機能を設定
     */
    private fun setupDisclosureToggle(newsBinding: jp.stocklinker.app.databinding.PaneNewsBinding) {
        // ヘッダータップで切り替え
        newsBinding.layoutDisclosureHeader.setOnClickListener {
            toggleDisclosureSection(newsBinding)
        }
        
        // 折りたたみ時のプレースホルダータップでも展開
        newsBinding.tvDisclosureCollapsed.setOnClickListener {
            toggleDisclosureSection(newsBinding)
        }
        
        // 初期状態を適用
        updateDisclosureToggleUI(newsBinding)
    }
    
    /**
     * 適時開示セクションの展開/折りたたみを切り替え
     */
    private fun toggleDisclosureSection(newsBinding: jp.stocklinker.app.databinding.PaneNewsBinding) {
        isDisclosureExpanded = !isDisclosureExpanded
        updateDisclosureToggleUI(newsBinding)
        saveDisclosureToggleState()
    }
    
    /**
     * 適時開示トグルのUI更新
     */
    private fun updateDisclosureToggleUI(newsBinding: jp.stocklinker.app.databinding.PaneNewsBinding) {
        if (isDisclosureExpanded) {
            // 展開状態
            newsBinding.rvTimelyDisclosure.visibility = View.VISIBLE
            newsBinding.tvDisclosureCollapsed.visibility = View.GONE
            newsBinding.ivDisclosureToggle.rotation = 180f  // 上向き矢印
            
            // セクションの高さを元に戻す
            val params = newsBinding.layoutDisclosureSection.layoutParams as LinearLayout.LayoutParams
            params.weight = 1f
            params.height = 0
            newsBinding.layoutDisclosureSection.layoutParams = params
        } else {
            // 折りたたみ状態
            newsBinding.rvTimelyDisclosure.visibility = View.GONE
            newsBinding.tvDisclosureCollapsed.visibility = View.VISIBLE
            newsBinding.ivDisclosureToggle.rotation = 0f  // 下向き矢印
            
            // セクションの高さを最小化
            val params = newsBinding.layoutDisclosureSection.layoutParams as LinearLayout.LayoutParams
            params.weight = 0f
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT
            newsBinding.layoutDisclosureSection.layoutParams = params
        }
    }
    
    /**
     * トグル状態を保存
     */
    private fun saveDisclosureToggleState() {
        getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(AppConstants.KEY_DISCLOSURE_EXPANDED, isDisclosureExpanded)
            .apply()
    }
    
    /**
     * トグル状態を読み込み
     */
    private fun loadDisclosureToggleState() {
        isDisclosureExpanded = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(AppConstants.KEY_DISCLOSURE_EXPANDED, true)  // デフォルトは展開
    }
    
    // ========== 適時開示トグルここまで ==========
    
    // ========== ヘッダートグル（v7.3.1シンプル版）==========
    
    /**
     * ヘッダートグルのセットアップ
     */
    private fun setupHeaderToggle() {
        // 初期状態の設定
        updateHeaderToggleUI()
        
        // トグルボタンのクリックリスナー
        binding.btnToggleHeader.setOnClickListener {
            isHeaderExpanded = !isHeaderExpanded
            animateHeaderToggle()
            saveHeaderToggleState()
        }

        // リンクドックを下スワイプで非表示
        binding.layoutHeaderControls.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val deltaY = event.rawY - startY
                        if (deltaY > 80) {
                            // 下方向スワイプでドックを閉じる
                            isHeaderExpanded = false
                            isDockExpanded = false
                            setDockExpanded(false)
                            animateHeaderToggle()
                            saveHeaderToggleState()
                        } else if (deltaY < -80) {
                            // 上方向スワイプでドックを拡張
                            isHeaderExpanded = true
                            isDockExpanded = true
                            setDockExpanded(true)
                            binding.layoutHeaderControls.visibility = View.VISIBLE
                            binding.btnToggleHeader.visibility = View.VISIBLE
                            binding.layoutMarketSection.visibility = View.VISIBLE
                        }
                        return true
                    }
                }
                return false
            }
        })
    }
    
    /**
     * アニメーション付きトグル
     */
    private fun animateHeaderToggle() {
        val layout = binding.layoutHeaderControls

        if (isHeaderExpanded) {
            // ふわっと表示
            layout.visibility = View.VISIBLE
            layout.alpha = 0f
            layout.translationY = 24f
            layout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .start()

            binding.btnToggleHeader.text = "🔼 リンクを隠す"
            binding.btnToggleHeader.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
        } else {
            // ふわっと非表示
            layout.animate()
                .alpha(0f)
                .translationY(24f)
                .setDuration(160)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    layout.visibility = View.GONE
                    layout.translationY = 0f
                }
                .start()

            binding.btnToggleHeader.text = "🔽 リンクを表示"
            binding.btnToggleHeader.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
        }
    }
    
    /**
     * ヘッダートグルUIの更新（初期化用）
     */
    private fun updateHeaderToggleUI() {
        val hasSelection = currentSelectedCode.isNotEmpty() || selectedMasterItem != null
        if (!hasSelection) {
            binding.layoutHeaderControls.visibility = View.GONE
            binding.btnToggleHeader.visibility = View.GONE
            return
        }

        binding.btnToggleHeader.visibility = View.VISIBLE
        if (isHeaderExpanded) {
            binding.layoutHeaderControls.visibility = View.VISIBLE
            setDockExpanded(isDockExpanded)
            binding.btnToggleHeader.text = "🔼 リンクを隠す"
            binding.btnToggleHeader.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
        } else {
            binding.layoutHeaderControls.visibility = View.GONE
            binding.btnToggleHeader.text = "🔽 リンクを表示"
            binding.btnToggleHeader.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
        }
    }

    private fun setDockExpanded(expanded: Boolean) {
        val dockParams = binding.layoutHeaderControls.layoutParams
        val scrollParams = binding.linkDockScroll.layoutParams as LinearLayout.LayoutParams
        if (expanded) {
            dockParams.height = (binding.paneMain.height * 0.65f).toInt().coerceAtLeast(320)
            scrollParams.height = 0
            scrollParams.weight = 1f
            binding.layoutMarketSection.visibility = View.VISIBLE
        } else {
            dockParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            scrollParams.weight = 0f
            binding.layoutMarketSection.visibility = View.GONE
        }
        binding.layoutHeaderControls.layoutParams = dockParams
        binding.linkDockScroll.layoutParams = scrollParams
    }
    private fun showLinkDockForSelection() {
        if (!isHeaderExpanded) {
            isHeaderExpanded = true
            isDockExpanded = false
            setDockExpanded(false)
            animateHeaderToggle()
            saveHeaderToggleState()
        } else {
            binding.layoutHeaderControls.visibility = View.VISIBLE
        }
        binding.btnToggleHeader.visibility = View.VISIBLE
        binding.layoutMarketSection.visibility = if (isDockExpanded) View.VISIBLE else View.GONE
    }

    private fun hideLinkDockForNoSelection() {
        isHeaderExpanded = false
        isDockExpanded = false
        binding.layoutHeaderControls.visibility = View.GONE
        binding.btnToggleHeader.visibility = View.GONE
        saveHeaderToggleState()
    }
    
    /**
     * ヘッダートグル状態を保存
     */
    private fun saveHeaderToggleState() {
        getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(AppConstants.KEY_HEADER_EXPANDED, isHeaderExpanded)
            .apply()
    }
    
    /**
     * ヘッダートグル状態を読み込み
     */
    private fun loadHeaderToggleState() {
        isHeaderExpanded = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(AppConstants.KEY_HEADER_EXPANDED, false)  // デフォルトは折りたたみ
    }
    
    // ========== ヘッダートグルここまで ==========
    
    // ========== スワイプナビゲーション（v6.7追加）==========
    
    /**
     * ニュース画面へスワイプで移動（シングルペイン時）
     */
    fun navigateToNews() {
        try {
            if (!isDualPane) {
                binding.slidingPaneLayout.openPane()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * メイン画面へ戻る（シングルペイン時）
     */
    private fun navigateBackToMain() {
        try {
            if (!isDualPane) {
                binding.slidingPaneLayout.closePane()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * スワイプナビゲーションのセットアップ
     */
    private fun setupSwipeNavigation() {
        try {
            // SlidingPaneLayoutのリスナー設定
            binding.slidingPaneLayout.addPanelSlideListener(object : SlidingPaneLayout.PanelSlideListener {
                override fun onPanelSlide(panel: View, slideOffset: Float) {
                    // スライド中の処理（必要に応じて）
                }
                
                override fun onPanelOpened(panel: View) {
                    // ニュースペインが開いた時にニュースを取得
                    if (!isDualPane) {
                        refreshNews()
                        refreshDisclosure()
                    }
                }
                
                override fun onPanelClosed(panel: View) {
                    // メインペインに戻った時の処理
                }
            })
            
            // バックキー処理
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.slidingPaneLayout.isOpen && !isDualPane) {
                        binding.slidingPaneLayout.closePane()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ========== スワイプナビゲーションここまで ==========
}

// === アダプター ===

class StockAdapter(
    private val items: List<StockItem>,
    private val onItemClick: (StockItem) -> Unit,
    private val onDeleteClick: (StockItem) -> Unit,
    private val onFavoriteClick: (StockItem) -> Unit,
    private val onMemoClick: (StockItem) -> Unit,
    private val onMoveClick: (StockItem) -> Unit,
    private val getGroupInfo: (String?) -> Group?
) : RecyclerView.Adapter<StockAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCode: TextView = view.findViewById(R.id.tvCode)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvGroup: TextView = view.findViewById(R.id.tvGroup)
        val tvMemo: TextView = view.findViewById(R.id.tvMemo)
        val btnFavorite: ImageButton = view.findViewById(R.id.btnFavorite)
        val btnMove: ImageButton = view.findViewById(R.id.btnMove)
        val btnMemo: ImageButton = view.findViewById(R.id.btnMemo)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val layoutItemTags: LinearLayout = view.findViewById(R.id.layoutItemTags)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stock, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvCode.text = item.code
        holder.tvName.text = item.name
        
        // グループ表示（Vector Drawableアイコン）
        val group = getGroupInfo(item.groupId)
        if (group != null) {
            holder.tvGroup.text = group.name
            holder.tvGroup.visibility = View.VISIBLE
            val iconDrawable = ContextCompat.getDrawable(holder.itemView.context, IconManager.getIconResId(group.icon))
            iconDrawable?.setBounds(0, 0, dpToPx(16f, holder.itemView.context).toInt(), dpToPx(16f, holder.itemView.context).toInt())
            val groupColor = try { Color.parseColor(group.color) } catch (e: Exception) { Color.GRAY }
            iconDrawable?.setTint(groupColor)
            holder.tvGroup.setCompoundDrawablesRelative(iconDrawable, null, null, null)
            holder.tvGroup.compoundDrawablePadding = dpToPx(4f, holder.itemView.context).toInt()
        } else {
            holder.tvGroup.visibility = View.GONE
            holder.tvGroup.setCompoundDrawablesRelative(null, null, null, null)
        }
        
        // メモ表示
        if (item.memo.isNotEmpty()) {
            holder.tvMemo.text = item.memo
            holder.tvMemo.visibility = View.VISIBLE
        } else {
            holder.tvMemo.visibility = View.GONE
        }
        
        // お気に入りアイコン
        holder.btnFavorite.setImageResource(
            if (item.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_border
        )
        
        // タグ表示
        holder.layoutItemTags.removeAllViews()
        if (item.sector.isNotEmpty() || item.themes.isNotEmpty()) {
            holder.layoutItemTags.visibility = View.VISIBLE
            
            if (item.sector.isNotEmpty()) {
                val tvSector = TextView(holder.itemView.context).apply {
                    text = item.sector
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(context, R.color.tag_sector_text))
                    setBackgroundResource(R.drawable.bg_tag_sector)
                    setPadding(dpToPx(6f, context).toInt(), dpToPx(2f, context).toInt(), dpToPx(6f, context).toInt(), dpToPx(2f, context).toInt())
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = dpToPx(4f, holder.itemView.context).toInt()
                tvSector.layoutParams = params
                holder.layoutItemTags.addView(tvSector)
            }
            
            for (theme in item.themes.take(1)) {
                val tvTheme = TextView(holder.itemView.context).apply {
                    text = theme
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(context, R.color.tag_theme_text))
                    setBackgroundResource(R.drawable.bg_tag_theme)
                    setPadding(dpToPx(6f, context).toInt(), dpToPx(2f, context).toInt(), dpToPx(6f, context).toInt(), dpToPx(2f, context).toInt())
                }
                holder.layoutItemTags.addView(tvTheme)
            }
        } else {
            holder.layoutItemTags.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
        holder.btnFavorite.setOnClickListener { onFavoriteClick(item) }
        holder.btnMemo.setOnClickListener { onMemoClick(item) }
        holder.btnMove.setOnClickListener { onMoveClick(item) }
    }

    override fun getItemCount() = items.size
    
    private fun dpToPx(dp: Float, context: android.content.Context): Float = dp * context.resources.displayMetrics.density
}

// サジェストアダプター
class SuggestAdapter(
    private val items: List<MasterStockItem>,
    private val onItemClick: (MasterStockItem) -> Unit
) : RecyclerView.Adapter<SuggestAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCode: TextView = view.findViewById(R.id.tvSuggestCode)
        val tvName: TextView = view.findViewById(R.id.tvSuggestName)
        val tvSector: TextView = view.findViewById(R.id.tvSuggestSector)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suggest, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvCode.text = item.code
        holder.tvName.text = item.name
        holder.tvSector.text = if (item.sector.isNotEmpty()) item.sector else item.market
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
