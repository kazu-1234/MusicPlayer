# Music Player (Android)

Jetpack Composeを使用して開発された、ローカルファイル再生に特化したAndroid向け多機能音楽プレイヤーアプリです。
シンプルでモダンなUIと、M3Uプレイリストの読み込み、文字化けの自動修復など、かゆいところに手が届く機能を搭載しています。

## 📸 スクリーンショット

| メイン画面（曲リスト） | プレイヤー画面 | プレイリスト管理 | 設定画面 |
| :---: | :---: | :---: | :---: |
| <img src="docs/images/screenshot_main.png" width="200"> | <img src="docs/images/screenshot_player.png" width="200"> | <img src="docs/images/screenshot_playlist.png" width="200"> | <img src="docs/images/screenshot_settings.png" width="200"> |

## ✨ 主な機能

* **バックグラウンド再生:** Foreground Serviceを利用し、アプリを閉じても安定して音楽を再生。
* **ライブラリ管理:** 指定したフォルダ（SDカード対応）をスキャンし、端末内の音楽を自動でリストアップ。
* **強力なプレイリスト連携:** M3U/M3U8ファイルの読み込みに対応。Windows環境で作成されたプレイリストの絶対パスも、ベースパスを設定することで自動的に相対パスとして解決します。
* **文字化け自動修復:** 古いMP3ファイル等で発生しがちなID3タグの文字化け（Latin-1からShift_JISへの誤変換など）を検知して自動で修復。
* **再生キューの並び替え:** 再生待ちの曲リストをドラッグ＆ドロップで直感的に並び替え可能。
* **システム連携:** ロック画面や通知パネルのシステムメディアプレイヤーからの操作に対応。
* **高度なソート・検索:** 曲名、アーティスト、アルバム、再生回数でのソート機能に加え、インクリメンタルサーチを搭載。
* **アルバムアートのキャッシュ:** メモリとディスクの二段構えのキャッシュにより、リストの高速スクロールを実現。
* **アプリ内アップデート:** GitHub Releases機能と連動し、アプリ内から直接新しいバージョンを確認・ダウンロード可能。

## 🚀 インストール方法

### 最新版のAPKをダウンロードする
[Releasesページ](https://github.com/kazu-1234/MusicPlayer/releases) から最新の `app-release.apk` をダウンロードし、Android端末にインストールしてください。

※ インストール時に「提供元不明のアプリ」の許可が求められる場合があります。

## 🛠 開発環境・使用技術

* **言語:** Kotlin
* **UIフレームワーク:** Jetpack Compose
* **アーキテクチャ・主要API:**
  * Foreground Service (バックグラウンド再生・スキャン)
  * MediaSessionCompat (システムメディアコントロール連携)
  * MediaMetadataRetriever (メタデータ・アルバムアート抽出)
  * Storage Access Framework / SAF (外部ストレージへのアクセス)
  * Coroutines (非同期処理)
* **最小SDK:** API 24 (Android 7.0)
* **ターゲットSDK:** API 36

## ⚙️ ビルド方法

1. このリポジトリをクローンします。
   `git clone https://github.com/kazu-1234/MusicPlayer.git`
2. Android Studioでプロジェクトを開きます。
3. Gradleの同期が完了したら、`Run` ボタンを押して実機またはエミュレータでビルド・実行します。

## 📝 今後のアップデート予定 (TODO)

* [ ] ウィジェットの追加
* [ ] ギャップレス再生の対応
* [ ] イコライザー機能の追加

## 📄 ライセンス

このプロジェクトは [MIT License](LICENSE) のもとで公開されています。詳細は `LICENSE` ファイルをご覧ください。
