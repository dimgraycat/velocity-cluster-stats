# Velocity Cluster Stats

Velocity Cluster Stats は、複数の Velocity Proxy の接続人数を Redis 経由で集計する Velocity Plugin です。

複数の Velocity Proxy から 1 台または複数台の Fabric backend server に接続する構成を想定しています。Fabric 側 Plugin は不要です。

English: [README.md](README.md)

## 必要環境

- Velocity `3.5.0-SNAPSHOT`
- Java 21
- Redis
- ローカルビルドには Gradle Wrapper

## 機能

- 複数 Velocity Proxy をまたいだ全体接続人数の集計
- public proxy node の台数と接続人数
- 任意の staff 専用 proxy node 対応
- staff node が存在しない public-only 構成への対応
- public node ごとの接続人数表示
- 全体、指定 node、staff group のユーザー名一覧
- Velocity の registered server 名を使った backend server 別人数表示
- 登録済み backend server は接続人数が0人でも表示
- 見出し、public/staff、数値、エラーを区別しやすいカラー表示
- Velocity 再起動なしで設定を反映する `/vstats reload`
- Redis 障害時も Velocity のログイン、チャット、backend 移動をブロックしないフェイルセーフ設計

## コマンド

| コマンド | 内容 | 権限 |
|---|---|---|
| `/vstats` | 全体サマリを表示 | `vstats.view` |
| `/vstats public` | public node の統計のみ表示 | `vstats.view` |
| `/vstats staff` | staff 統計のみ表示 | `vstats.staff` |
| `/vstats servers` | backend server 別人数のみ表示 | `vstats.view` |
| `/vstats list` | ユーザー名一覧を表示 | `vstats.list` |
| `/vstats list public` | public node のユーザー名一覧を表示 | `vstats.list` |
| `/vstats list <nodeId>` | 指定 Velocity node のユーザー名一覧を表示 | `vstats.list` |
| `/vstats list staff` | staff node のユーザー名一覧を表示 | `vstats.list` + `vstats.staff` |
| `/vstats reload` | config と Redis 設定を再読み込み | `vstats.reload` |
| `/vstats help` | 利用可能な vstats コマンドを表示 | なし |

Console からの実行は管理者操作として扱います。Player の権限判定は Velocity の Permission API に委譲します。

## パーミッション

この Plugin は LuckPerms API へ直接依存しません。Velocity の Permission API を使うため、Velocity 対応の権限 Plugin で以下の node を付与してください。

| 権限 | 許可される操作 |
|---|---|
| `vstats.view` | `/vstats`, `/vstats public`, `/vstats servers` |
| `vstats.staff` | `/vstats staff`, `/vstats list staff`, staff node の player list |
| `vstats.list` | `/vstats list`, `/vstats list public`, `/vstats list <nodeId>` |
| `vstats.reload` | `/vstats reload` |

`vstats.*` を付与すると、Velocity Cluster Stats の全コマンドを許可できます。
`/vstats help` は permission node を持たず、実行者が使えるコマンドのみ表示します。

LuckPerms の設定例:

```text
/lpv group admin permission set vstats.* true

/lpv group admin permission set vstats.view true
/lpv group admin permission set vstats.staff true
/lpv group admin permission set vstats.list true
/lpv group admin permission set vstats.reload true

/lpv group moderator permission set vstats.view true
/lpv group moderator permission set vstats.list true

/lpv group default permission set vstats.view true
```

全権限を付与するだけなら `vstats.*` の1行で十分です。個別の permission node は細かく制御したい場合に使います。

`vstats.staff` がない場合、`/vstats list` は public player のみ表示します。staff 統計と staff player list には `vstats.staff` が必要です。

## インストール

1. `VelocityClusterStats-<version>.jar` をビルドまたはダウンロードします。
2. 各 Velocity Proxy の `plugins/` に jar を配置します。
3. Velocity を一度起動し、設定ファイルを生成します。

   ```text
   plugins/velocity-cluster-stats/config.yml
   ```

4. 各 proxy node の `config.yml` を編集します。
5. Velocity を再起動するか、`/vstats reload` を実行します。

Fabric backend server にはこの Plugin を入れないでください。

## 設定

各 Velocity node には一意な `node.id` を設定してください。

```yaml
node:
  id: "prx01"
  group: "public"
```

利用できる group:

- `public`: 通常の proxy node
- `staff`: staff 専用 proxy node

staff node は任意です。active な staff node が存在しない場合、`/vstats` は `[Staff]` ブロックを省略し、server 行にも `Staff 0` を表示しません。

Redis 設定は全 Velocity node で共有します。

```yaml
redis:
  host: "127.0.0.1"
  port: 6379
  password: ""
  database: 0
  key-prefix: "vstats"
  connection-timeout-millis: 1000
  socket-timeout-millis: 1000
  failure-log-cooldown-seconds: 60
```

Redis timeout は短めにしてください。Redis は stats 用の共有ストアであり、Redis 障害時も通常の Velocity 動作には影響させない設計です。

## ビルド

JDK 21 を使います。

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build
```

配布用 jar:

```text
build/libs/VelocityClusterStats-<version>.jar
```

`-thin.jar` は runtime dependency を含まないため、通常の配布用 jar ではありません。

## テスト

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test
```

## リリース

GitHub Actions は `v` で始まる tag が push された時だけ Release を公開します。

例:

```sh
git tag v0.1.0
git push origin v0.1.0
```

workflow は test、build を実行し、thin jar を除外して `VelocityClusterStats-<version>.jar` を GitHub Release にアップロードします。
