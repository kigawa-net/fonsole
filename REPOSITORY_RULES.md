# リポジトリ運用規約

## 1. ブランチ管理

### ブランチ構成
| ブランチ | 説明 |
|----------|------|
| `main` | 本番用ブランチ。安定したリリースバージョンのみ存在する |
| `develop` | 開発用ブランチ。日常的な開発作業はここで行う |

### ブランチルール
- 日常的な開発は `develop` ブランチで行う
- `main` ブランチへのマージはリリース時のみ行う
- 機能開発やバグ修正は `develop` からフィーチャーブランチを切って作業する
- フィーチャーブランチ命名規則: `feature/<機能名>`, `fix/<バグ名>`, `refactor/<対象>` など

## 2. コミット規約

### コミットメッセージ形式
```
<type>: <subject>

<body> (オプション)
```

### type の種類
| type | 説明 |
|------|------|
| `feat` | 新機能の追加 |
| `fix` | バグ修正 |
| `refactor` | リファクタリング（機能変更なし） |
| `docs` | ドキュメントのみの変更 |
| `style` | コードフォーマット、セミコロンなど（機能に影響ない変更） |
| `test` | テストの追加・修正 |
| `chore` | ビルドツール、依存関係など雑務 |

### 例
```
feat: add support for restoring specific directories
fix: handle null date gracefully in restore command
refactor: modularize project by introducing fonsole-cli submodule
```

## 3. プルリクエスト規約

### 基本ルール
- `develop` ブランチへの PR はレビュー後マージする
- `main` ブランチへの PR はリリース時のみ許可する
- PR 作成前にローカルでテストを実行し、全テストが通過することを確認する
- PR のタイトルはコミット規約に従う

### マージ方法
- 原則として Squash Merge を使用する
- マージコミットメッセージはコミット規約に従う

## 4. リリース・バージョン管理

### リリースフロー
1. `develop` で変更を確定させる
2. `main` ブランチにマージする
3. タグを切ってリリースする

### Docker イメージ
- リリース先: `harbor.kigawa.net`
- イメージ名: `kigawa-net/fonsole-cli`
- タグ付け:
  - `latest`: 最新安定版
  - `<branch>`: ブランチ名
  - `<ref_name>-<sha>`: 特定コミット

## 5. CI/CD 規約

### ワークフロー構成
| ワークフロー | トリガー | 説明 |
|-------------|---------|------|
| `test.yml` | `develop` への push/PR | テスト実行 |
| `fonsole-cli.yml` | `main` への push | リリースパイプライン |

### 新規ワークフロー追加時
- `.github/workflows/` 配下に YAML ファイルを配置する
- 既存の `release.yml` を `workflow_call` で再利用することを推奨する

## 6. コードスタイル

### Kotlin
- Kotlin 公式コードスタイルを適用する
- JSR305 アノテーションは strict モードでチェックする
- コルーチンを活用した非同期処理を標準とする

### 一般的なルール
- 1 行の長さは過度に長くならないよう配慮する
- 不要な import は削除する
- 未使用変数・定数は削除する

## 7. ドキュメント管理

### 基本ルール
- 重要な変更は `CLAUDE.md` に記録する
- API 仕様やアーキテクチャ図は `fonsole-doc/` 配下に配置する
- README はプロジェクト概要と基本的な使用法のみ記載する

## 8. サブモジュール管理

### fonsole-doc
- ドキュメントは `fonsole-doc` サブモジュールとして管理する
- サブモジュールの更新時は明示的にコミットメッセージに記録する

## 9. 環境設定

### 機密情報
- `.env` は Git にコミットするが、`.env.local` は `.gitignore` で除外する
- GitHub Secrets に機密情報を保存する（`HARBOR_PASS`, `GIT_TOKEN` など）

## 10. 依存関係管理

### Gradle
- Version Catalog (`gradle/libs.versions.toml`) で依存関係を一元管理する
- 依存関係の更新は PR で提案し、レビュー後適用する
