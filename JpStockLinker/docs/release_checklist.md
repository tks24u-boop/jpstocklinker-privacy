# 📋 Google Play Store 公開チェックリスト

## ✅ 完了済み

- [x] プライバシーポリシー作成（`docs/privacy_policy.html`）
- [x] Play Store説明文作成（`docs/play_store_listing.md`）
- [x] バージョン更新（7.3.1 / versionCode 731）
- [x] ProGuardルール設定
- [x] リリースビルド設定（minify + shrink有効）

---

## 📝 対応が必要な項目

### 1. applicationId の変更
`app/build.gradle.kts` の `applicationId` を変更：

```kotlin
// 変更前
applicationId = "com.example.jpstocklinker"

// 変更後（例）
applicationId = "com.yourname.jpstocklinker"
// または
applicationId = "jp.stocklinker.app"
```

⚠️ `com.example` は Play Store に公開できません

---

### 2. プライバシーポリシーの公開

`docs/privacy_policy.html` を以下のいずれかで公開：

1. **GitHub Pages**（無料・おすすめ）
   - GitHubリポジトリを作成
   - Settings → Pages で公開
   - URL例: `https://yourusername.github.io/jpstocklinker/privacy_policy.html`

2. **Google Sites**（無料）
   - sites.google.com で作成

3. **自前のWebサーバー**

---

### 3. 署名用キーストアの作成

Android Studioで作成：

1. **Build** → **Generate Signed Bundle / APK**
2. **Android App Bundle** を選択
3. **Create new...** でキーストアを作成
   - Key store path: `release-key.jks`（安全な場所に保存）
   - Password: 強力なパスワード
   - Alias: `release`
   - Validity: 25年以上

⚠️ **重要**: キーストアファイルとパスワードは絶対に紛失しないこと！

---

### 4. スクリーンショットの準備

必要なスクリーンショット：
- **スマートフォン**: 最低2枚（推奨4-8枚）
- サイズ: 縦長 1080x1920 以上

撮影すべき画面：
1. メイン画面（銘柄リスト）
2. ホーム画面ウィジェット
3. リンク一覧（展開時）
4. グループ機能
5. 検索・サジェスト

---

### 5. アイコンの確認

高解像度アイコン（512x512 PNG）が必要：
- 場所: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- または別途512x512を用意

---

### 6. Play Console での設定

1. **アプリを作成**
2. **ストアの掲載情報**
   - アプリ名: JpStockLinker - 日本株リンカー
   - 短い説明: `docs/play_store_listing.md` から
   - 長い説明: 同上
   - スクリーンショット: アップロード
3. **プライバシーポリシー URL** を設定
4. **コンテンツレーティング** の質問に回答
5. **ターゲットユーザー** を設定（13歳以上）
6. **リリース** → **製品版** → AABをアップロード

---

## 🚀 リリースビルドの作成手順

### Android Studioで：

1. **Build** → **Generate Signed Bundle / APK**
2. **Android App Bundle** を選択
3. キーストアを選択
4. **release** ビルドを選択
5. **Finish**

生成場所: `app/build/outputs/bundle/release/app-release.aab`

---

## ⚠️ 注意事項

- applicationId は一度公開すると変更不可
- キーストアを紛失するとアップデート不可能
- 最初のレビューには数日〜1週間かかることがある
- リジェクトされた場合は理由を確認して修正・再申請

---

## 📞 サポート

何か問題があれば、以下を確認：
- [Google Play Console ヘルプ](https://support.google.com/googleplay/android-developer/)
- [Android デベロッパーガイド](https://developer.android.com/distribute)
