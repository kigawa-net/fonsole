# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

FonsoleはKotlinベースのCLIバックアップ・復元ツールで、MongoDBをストレージとして使用します。プロジェクトディレクトリをMongoDBにバックアップし、特定の日付から復元することを目的としています。包括的なユーティリティライブラリ（`kutil`）を含み、コマンドベースのアーキテクチャに従っています。

## ビルドと開発コマンド

### プロジェクトのビルド
- **JARのビルド**: `./gradlew shadowJar` - `build/libs/fonsole-all.jar`にファットJARを作成
- **クリーンビルド**: `./gradlew clean build`
- **アプリケーション実行**: `./gradlew run --args="<command>"`

### Docker開発環境
- **Dockerイメージのビルド**: `docker build -t fonsole .`
- **Docker Composeで実行**: `docker-compose -f docker-compose.dev.yml up -d` (MongoDB + Mongo Expressを起動)
- **MongoDBアクセス**: localhost:27017 (mongo/mongo認証情報)
- **Mongo Express UI**: http://localhost:8081 (mongo/mongo認証情報)

### テストと品質管理
- **テスト実行**: `./gradlew test`
- **依存関係の更新確認**: `./gradlew dependencyUpdates` (利用可能な場合)

## アーキテクチャ

### メインアプリケーション構造
- **エントリーポイント**: `src/main/kotlin/net/kigawa/fonsole/Main.kt` - コマンドラインパーサーとアプリケーションブートストラップ
- **コマンド**: `src/main/kotlin/net/kigawa/fonsole/Cmds.kt` - コマンド登録enum
- **コマンド実装**:
  - `cmd/BackupCmd.kt` - プロジェクトのMongoDBへのバックアップを処理
  - `cmd/RestoreCmd.kt` - 特定のバックアップ日付からプロジェクトを復元

### コアコンポーネント
- **エディター**: バックアップ・復元操作のビジネスロジックを処理
  - `editor/BackupEditor.kt` - バックアップの作成とクリーンアップを管理
  - `editor/ProjectEditor.kt` - プロジェクトのセットアップとメタデータを処理
- **MongoDB統合**: `mongo/`パッケージ内のカスタムMongoDBクライアントラッパー
- **設定**: `config/`パッケージ内でdotenvを使用した環境ベースの設定
- **モデル**: ファイル、ディレクトリ、データベースドキュメント用のデータモデル

### ユーティリティライブラリ（kutil）
プロジェクトには以下を含む広範囲なユーティリティライブラリが含まれています：
- **I/Oユーティリティ**: 非同期操作のためのチャンネルベースリーダー/ライター
- **プロセスユーティリティ**: サブプロセス管理
- **ドメインユーティリティ**: URL解析、ホスト名検証、プロトコル処理
- **バリデーション**: 文字列と汎用バリデーター
- **並行性**: コルーチンヘルパーと例外処理
- **データ構造**: 拡張されたリスト操作とイテラブル
- **依存関係管理**: サイクル検出付きの依存関係解決
- **差分ユーティリティ**: ファイル比較と差分検出

### 主要な依存関係
- **MongoDB**: Kotlinコルーチンドライバー（`mongodb-driver-kotlin-coroutine`）
- **ログ**: 設定可能なレベルを持つLogback
- **環境**: 設定用のdotenv-kotlin
- **コルーチン**: 非同期操作用のkotlinx-coroutines-core

## 設定

### 環境変数
`.env`または`.env.local`ファイルで設定：

**必須**:
- `MONGO_USERNAME` - MongoDBユーザー名
- `MONGO_PASSWORD` - MongoDBパスワード
- `BACKUP_PATH` - バックアップ・復元するディレクトリ
- `PROJECT_NAME` - プロジェクト識別子

**オプション**:
- `MONGO_HOST` (デフォルト: localhost)
- `MONGO_PORT` (デフォルト: 27017)
- `MONGO_DATABASE_NAME` (デフォルト: fonsole)
- `LOG_LEVEL` (デフォルト: INFO)
- `MONGO_LOG_LEVEL` (デフォルト: INFO)
- `RESTORE_DATE` - 復元用の特定日付（フォーマット: "yyyy-MM-dd HH:mm:ss"）
- `RESTORE_TARGET_DIRECTORY` - 復元対象の特定ディレクトリパス（例: "src/main"）
- `MAX_REQUEST` (デフォルト: 10)

## 使用コマンド

### バックアップ
```bash
java -jar fonsole-all.jar backup
```
設定されたディレクトリの新しいバックアップを作成します。

### 復元
```bash
# 最新のバックアップを復元
java -jar fonsole-all.jar restore

# 特定の日付から復元（.envでRESTORE_DATEを設定）
RESTORE_DATE="2024-01-15 10:30:00" java -jar fonsole-all.jar restore

# 特定のディレクトリのみを復元
RESTORE_TARGET_DIRECTORY="src/main" java -jar fonsole-all.jar restore

# 特定の日付から特定のディレクトリのみを復元
RESTORE_DATE="2024-01-15 10:30:00" RESTORE_TARGET_DIRECTORY="src/main" java -jar fonsole-all.jar restore
```

## 開発ノート

### コードスタイル
- Kotlin公式コードスタイルが適用されます
- 全体にわたってコルーチンベースの非同期操作
- シールドクラスとデータクラスの広範囲な使用
- `kutil.domain.result`でのResultベースのエラーハンドリングパターン

### ファイル構造パターン
- 機能別パッケージ構成
- コマンド、エディター、モデル、設定間の明確な分離
- カスタムクライアントラッパーを通じて抽象化されたMongoDB操作
- `kutil`パッケージ内でドメイン別に整理されたユーティリティ関数

### エラーハンドリング
- 特定ドメイン用のカスタム例外タイプ（例：`TransactionAbortException`）
- 失敗する可能性のある操作にはResultパターン
- 設定可能なレベルでの構造化ログ
- 適切な終了コードでのコマンド実行での優雅なエラーハンドリング