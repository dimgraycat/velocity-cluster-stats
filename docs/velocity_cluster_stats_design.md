# Velocity Cluster Stats Plugin 仕様書・設計書

版: コマンド固定 + 複数 Fabric Server 対応 + config自動生成 + Redisフェイルセーフ + `/vstats reload` + `/vstats servers` + staff node 任意対応 + staffなし表示省略版 `/vstats` / `public` / `staff` / `servers` / `list` / `reload` / display-name 廃止

作成日: 2026-05-09  
対象: Codex による実装用  
実装対象: Velocity Plugin のみ  
Backend: Fabric Server Minecraft 26.1.2 / 1台または複数台  
Proxy: Velocity 3.5.0-SNAPSHOT / 複数台  
共有ストア: Redis / 1台

---

## 1. 目的

複数台の Velocity Proxy から 1 台または複数台の Fabric Server へ接続している構成で、Velocity Plugin のコマンドだけで以下を確認できるようにする。

- 全体の接続人数
- public 用 Velocity の台数と接続人数
- staff 専用 Velocity が存在する場合の台数と接続人数
- staff 専用 Velocity が存在しない構成でも正常表示すること
- public 用 Velocity ごとの接続人数
- 指定 Velocity または staff group に接続しているユーザー名一覧
- Fabric Server が複数台になった場合の backend server 別接続人数
- backend server 別人数を public / staff に分けて表示する `/vstats servers`
- Velocity を再起動せずに設定を再読み込みする `/vstats reload`

Fabric Server 側には Plugin を入れない。  
プレイヤーが Fabric Server にログイン中に `/vstats` を実行しても、Velocity 側の Proxy Command として処理する。  
初回起動時に設定ファイルが存在しない場合はテンプレートを自動生成する。Redis 未設定・未起動・接続不可の場合でも、通常の Velocity のログイン・移動・チャット・サーバー接続処理を止めない。
staff 専用 Velocity は任意とし、staff node が1台も存在しない public のみの構成でも利用できる。staff node が存在しない場合、通常の `/vstats` では `[Staff]` ブロックを表示しない。さらに `[stats]` の `staff=0` や `[Servers]` の `Staff 0` も表示しない。ただし `/vstats staff` や `/vstats list staff` のように staff を明示したコマンドは0件として正常表示する。

---

## 2. 全体構成

```text
Client
  ↓
複数台 Velocity 3.5.0-SNAPSHOT
  ├─ prx01: group=public
  ├─ prx02: group=public
  ├─ prx03: group=public
  └─ staff: group=staff  # 任意。存在しない構成でも可
       ↓ heartbeat / player snapshot / backend snapshot
Redis 1台
       ↑ read
任意の Velocity 上で /vstats を実行
  ↓
Fabric Server Minecraft 26.1.2
  ├─ lobby
  ├─ main
  └─ game01
```

各 Velocity は定期的に Redis へ自分の状態を書き込む。

書き込む内容:

- node id
- node group: `public` または `staff`
- 現在接続人数
- 接続中プレイヤー名一覧
- 各プレイヤーの接続先 backend server 名
- backend server 別人数
- 最終更新時刻

コマンドを実行した Velocity は Redis から全 node の状態を読み取り、集計して表示する。

staff node は任意である。Redis snapshot 内に `group=staff` の active node が1件も存在しない場合でもエラーにはしない。通常の `/vstats` では `[Staff]` ブロックを省略し、`Active Velocity Nodes` の `staff=0` も表示しない。`/vstats servers` でも `Staff 0` 列は表示しない。ただし `/vstats staff` と `/vstats list staff` は0件として正常表示する。

---

## 3. コマンド仕様

実装するコマンドは以下のみとする。

| コマンド | 内容 | 想定権限 |
|---|---|---|
| `/vstats` | 全体サマリを表示する | `vstats.view` |
| `/vstats public` | public 項目のみ表示する | `vstats.view` |
| `/vstats staff` | staff 項目のみ表示する。staff node がなくても0件表示する | `vstats.staff` |
| `/vstats servers` | servers 項目のみ表示する | `vstats.view` |
| `/vstats list` | 接続中ユーザー名一覧を表示する | `vstats.list` |
| `/vstats list public` | public group に接続しているユーザー名一覧を表示する | `vstats.list` |
| `/vstats list <nodeId>` | 指定 Velocity に接続しているユーザー名一覧を表示する | `vstats.list` |
| `/vstats list staff` | staff group に接続しているユーザー名一覧を表示する。staff node がなくても0件表示する | `vstats.staff` + `vstats.list` |
| `/vstats reload` | config を再読み込みし、Redis接続・heartbeat・権限設定を反映する | `vstats.reload` |
| `/vstats help` | 利用可能な vstats コマンドを表示する | なし |

Fabric Server が複数台になった場合、backend server 別の人数は `/vstats` の `[Servers]` ブロックで表示する。
`/vstats servers` は `[Servers]` ブロックのみを表示する。
`/vstats list <backendServer>` のような backend 指定 list は今回の仕様では実装しない。
staff node が存在しない構成でも `/vstats`, `/vstats public`, `/vstats staff`, `/vstats servers`, `/vstats list`, `/vstats list public`, `/vstats list staff`, `/vstats reload`, `/vstats help` は利用可能にする。

### 3.1 実装しないコマンド

以下は実装しない。

- `/vstats nodes`
- `/vstats groups`
- `/vstats players`
- `/vstats json`
- `/vstats redis`
- `/vstats summary`

Codex 実装時も、この仕様外コマンドは追加しない。

---

## 4. 表示仕様

### 4.1 `/vstats`

`/vstats` は以下の形式で固定表示する。実際のチャット表示では後述のカラーリングを適用する。

```text
========================================
[Stats]
Active Velocity Nodes: 4,  public=3 / staff=1
Total Players: 45

[Public]
prx01: 13 players
prx02: 16 players
prx03: 13 players
Total: 42 players

[Staff]
staff: 3 players

[Servers]
game01: Public 9, Staff 1
lobby: Public 10, Staff 0
main: Public 23, Staff 2
Total: 45 players, Public 42, Staff 3
```

staff node が存在しない場合の例:

```text
========================================
[Stats]
Active Velocity Nodes: 3,  public=3
Total Players: 42

[Public]
prx01: 13 players
prx02: 16 players
prx03: 13 players
Total: 42 players

[Servers]
game01: Public 9
lobby: Public 10
main: Public 23
Total: 42 players
```

この場合、`/vstats` の `[Staff]` ブロックは表示しない。
`Active Velocity Nodes` の `staff=0` と `[Servers]` の `Staff 0` も表示しない。
これにより、staff 専用 node を用意していない public のみ構成では public 構成として自然に見える表示にする。

仕様:

- `Active Velocity Nodes` は Redis 上で TTL 内の node 数を表示する。
- `public=3` は `node.group=public` の active node 数。
- `staff=1` は `node.group=staff` の active node 数。staff node が存在しない場合は ` / staff=0` を表示せず、`Active Velocity Nodes: 3,  public=3` のように表示する。
- `Total Players` は public + staff の合計人数。
- `[Public]` には public node を node id 昇順で表示する。
- `[Public]` の最後に `Total: X players` を表示する。
- `[Staff]` は staff node の個別名を出さず、`staff: X players` の1行に集約する。staff node が存在しない場合、`/vstats` では `[Staff]` ブロック自体を表示しない。
- `[Servers]` は backend server 名を昇順で表示する。
- `[Servers]` の各 server 行は、staff node が存在する場合は `serverName: Public X, Staff Y` 形式で表示する。staff node が存在しない場合は `serverName: Public X` 形式にする。
- `[Servers]` の `Total` は、staff node が存在する場合は `Total: X players, Public Y, Staff Z` 形式で表示する。staff node が存在しない場合は `Total: X players` のみ表示する。
- `[Servers]` の区切りは `, ` を使用する。` / ` は `Active Velocity Nodes: 4,  public=3 / staff=1` のような短い対比表示に限定する。
- `[Servers]` の `Total` の players は public + staff の合計人数と一致する。
- backend server が1台のみの場合も `[Servers]` は表示してよい。
- プレイヤーが0人でもブロックは表示する。

#### 4.1.1 staff node が存在しない場合の staff 表示省略

`/vstats` では、active な `group=staff` node が1件も存在しない場合、`[Staff]` ブロックを表示しない。

判定条件:

```text
activeStaffNodeCount == 0
```

この条件を満たす場合、通常の `/vstats` と `/vstats servers` では以下を表示しない。

- `Active Velocity Nodes` の ` / staff=0`
- 通常の `/vstats` 内の `[Staff]` ブロック
- `[Servers]` 各行の `, Staff 0`
- `[Servers] Total` の `, Public X, Staff 0`

以下は非表示にしない。

- `/vstats staff` の `[Staff] staff: 0 players`
- `/vstats list staff` の `There are 0 player(s):`

staff node が存在しているが接続人数が0人の場合は、`[Staff]` ブロックを表示する。

```text
[Staff]
staff: 0 players
```


0人の場合の例:

```text
========================================
[Stats]
Active Velocity Nodes: 4,  public=3 / staff=1
Total Players: 0

[Public]
prx01: 0 players
prx02: 0 players
prx03: 0 players
Total: 0 players

[Staff]
staff: 0 players

[Servers]
game01: Public 0, Staff 0
lobby: Public 0, Staff 0
main: Public 0, Staff 0
Total: 0 players, Public 0, Staff 0
```

---

### 4.2 `/vstats public`

`/vstats public` は `/vstats` の `[Public]` 項目だけを表示する。

```text
[Public]
prx01: 13 players
prx02: 16 players
prx03: 13 players
Total: 42 players
```

仕様:

- public node を node id 昇順で表示する。
- staff 情報は表示しない。
- 最後に public 合計人数を表示する。

---

### 4.3 `/vstats staff`

`/vstats staff` は `/vstats` の `[Staff]` 項目だけを表示する。

```text
[Staff]
staff: 3 players
```

仕様:

- staff node の個別名は表示しない。
- staff group 全体の合計人数のみ表示する。
- staff node が存在しない場合もエラーにせず、`staff: 0 players` と表示する。これは `/vstats staff` を明示実行した場合の表示であり、通常の `/vstats` では `[Staff]` ブロックを省略する。
- 実行には `vstats.staff` 権限を必要とする。

権限がない場合:

```text
You do not have permission to view staff stats.
```

---

### 4.4 `/vstats list`

`/vstats list` は接続中ユーザー名一覧を whitelist コマンド風に表示する。

```text
There are 45 player(s): xxx, yyy, zzzz
```

仕様:

- デフォルトでは全体の接続ユーザー名を表示する。
- staff 情報を含めるかは `vstats.staff` 権限で制御する。
- `vstats.staff` 権限がない実行者には public のユーザー名だけを表示する。
- `/vstats list public` は public group の全 node のプレイヤー名を集約して表示する。
- 表示順はユーザー名の大文字小文字を無視した昇順とする。
- ユーザーが0人の場合も同じ形式で表示する。

0人の場合:

```text
There are 0 player(s):
```

---

### 4.5 `/vstats list <nodeId>`

指定した Velocity node に接続しているユーザー名一覧を表示する。

例:

```text
/vstats list prx01
```

表示:

```text
There are 13 player(s): playerA, playerB, playerC
```

仕様:

- `<nodeId>` は config の `node.id` と一致させる。
- 対象 node が public の場合は `vstats.list` 権限のみで表示可能。
- 対象 node が staff の場合は `vstats.list` + `vstats.staff` 権限が必要。
- 対象 node が存在しない、または TTL 切れの場合は以下を表示する。

```text
Velocity node not found: prx99
```

---

### 4.6 `/vstats list staff`

staff group に接続しているユーザー名一覧を表示する。

```text
/vstats list staff
```

表示:

```text
There are 3 player(s): staffA, staffB, staffC
```

仕様:

- `staff` は node id ではなく group alias として扱う。
- staff group の全 node のプレイヤー名を集約する。
- staff node が存在しない場合もエラーにせず、`There are 0 player(s):` と表示する。
- 実行には `vstats.list` + `vstats.staff` 権限を必要とする。

権限がない場合:

```text
You do not have permission to view staff player list.
```

---

### 4.7 `/vstats servers`

`/vstats servers` は `/vstats` の `[Servers]` 項目だけを表示する。

```text
[Servers]
game01: Public 9, Staff 1
lobby: Public 10, Staff 0
main: Public 23, Staff 2
Total: 45 players, Public 42, Staff 3
```

仕様:

- 実行には `vstats.view` 権限を必要とする。
- server 名は Velocity の `velocity.toml` に定義されている registered server 名を使う。
- 表示順は server 名の大文字小文字を無視した昇順とする。
- 各 server 行は `serverName: Public X, Staff Y` 形式で表示する。
- `Public` は `node.group=public` の Velocity 経由でその backend server に接続している人数。
- `Staff` は `node.group=staff` の Velocity 経由でその backend server に接続している人数。staff node が存在しない場合は `Staff` 列を表示しない。
- `Total` は `Total: X players, Public Y, Staff Z` 形式で表示する。
- `X players` は Public + Staff の合計人数。
- 区切りは `, ` を使用する。
- `backend.enabled=true` の場合、Velocity の registered server は接続人数が0人でも表示する。
- `unassigned` は registered server ではないため、未割り当てプレイヤーが存在する場合のみ表示する。

---

### 4.8 Fabric Server が複数台の場合の `/vstats` 表示

Fabric Server が複数台になった場合は、Velocity 側でプレイヤーごとの接続先 backend server を取得し、`/vstats` に `[Servers]` ブロックを追加して表示する。

例:

```text
[Servers]
game01: Public 9, Staff 1
lobby: Public 10, Staff 0
main: Public 23, Staff 2
Total: 45 players, Public 42, Staff 3
```

仕様:

- server 名は Velocity の `velocity.toml` に定義されている registered server 名を使う。
- 表示順は server 名の大文字小文字を無視した昇順とする。
- `Total` は backend server 別人数の合計を public / staff 別に表示する。
- `Total Players` と `[Servers] Total` の players は原則一致する。
- ただし、ログイン直後やサーバー移動中などで backend 未接続のプレイヤーがいる場合は、`unassigned` として集計する。
- `unassigned` はプレイヤーが Velocity には接続済みだが、まだ backend server に接続完了していない状態を表す。
- `unassigned` は `[Servers]` に表示する。

例:

```text
[Servers]
game01: Public 9, Staff 1
lobby: Public 10, Staff 0
main: Public 22, Staff 2
unassigned: Public 1, Staff 0
Total: 45 players, Public 42, Staff 3
```

`/vstats public` と `/vstats staff` は従来どおり、それぞれ `[Public]` / `[Staff]` のブロックのみを表示する。staff node が存在しない場合、通常の `/vstats` では `[Staff]` を省略するが、`/vstats staff` は `staff: 0 players` を表示する。  
backend server 別表示は `/vstats` と `/vstats servers` にのみ含める。

`/vstats list` 系は backend server 指定を追加しない。  
今回の仕様で list 対象にできるのは、全体、Velocity node id、または `staff` group のみとする。

### 4.9 `/vstats help`

`/vstats help` は利用可能な vstats コマンドを、コンソールでも読みやすい1行1コマンド形式で表示する。

Console から実行した場合の例:

```text
> /vstats
> /vstats public
> /vstats staff
> /vstats servers
> /vstats list
> /vstats list public
> /vstats list <nodeId>
> /vstats list staff
> /vstats reload
> /vstats help
```

仕様:

- Redis snapshot は読まない。
- Console は管理者操作として扱い、全コマンドを表示する。
- Player から実行した場合は、その Player が実行可能なコマンドのみ表示する。
- `/vstats help` 自体は権限なしで表示できる。
- Velocity console が付与する時刻や `INFO` prefix は Plugin 側では出力しない。

---

### 4.10 `/vstats reload`

`/vstats reload` は config 再読み込みを開始したことを即時表示し、実際の reload 処理は非同期で行う。

即時表示:

```text
[vstats] Reloading config...
```

reload 成功時:

```text
[vstats] Reload completed.
```

reload は成功したが Redis に接続できない場合:

```text
[vstats] Reload completed, but Redis is not available.
```

config 読み込みまたは検証に失敗した場合:

```text
[vstats] Reload failed. Check console logs.
```

重要:

- `/vstats reload` は Velocity の通常処理スレッドで Redis 接続確認を同期実行しない。
- Redis 接続失敗は reload 失敗ではなく、stats 機能の一時的な degraded 状態として扱う。
- config の構文エラーや必須項目の不正値は reload 失敗として扱い、現在有効な設定を維持する。
- reload 中でもプレイヤーのログイン、チャット、backend 移動を止めない。

---

## 5. 設定ファイル仕様

`plugins/velocity-cluster-stats/config.yml`

`display-name` 項目は不要とする。表示・Redis保存・集計には `node.id` を使用する。

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

node:
  id: "prx01"
  group: "public"

heartbeat:
  interval-seconds: 5
  ttl-seconds: 15

backend:
  enabled: true
  unassigned-name: "unassigned"

command:
  primary: "vstats"
  snapshot-cache-millis: 1000
  player-list-limit: 100

permissions:
  view: "vstats.view"
  staff: "vstats.staff"
  list: "vstats.list"
  reload: "vstats.reload"
```


### 5.1 初回起動時の config 自動生成

Plugin 起動時、以下のパスに設定ファイルが存在しない場合はテンプレートを自動生成する。

```text
plugins/velocity-cluster-stats/config.yml
```

仕様:

- `plugins/velocity-cluster-stats/` ディレクトリが存在しない場合は作成する。
- `config.yml` が存在しない場合、Plugin jar 内に同梱した default config をコピーして作成する。
- 既存の `config.yml` がある場合は上書きしない。
- 自動生成後も Plugin は Velocity の通常動作を止めてはならない。
- 自動生成直後は Redis が未設定または未起動である可能性が高いため、Redis 接続失敗を致命的エラーにしない。
- config 生成後に Redis 接続へ失敗した場合でも、Plugin 全体ではなく stats 機能のみを degraded 状態として扱う。

起動ログ例:

```text
[velocity-cluster-stats] config.yml was not found. Generated default config: plugins/velocity-cluster-stats/config.yml
[velocity-cluster-stats] Redis is not available. Stats commands will show an error until Redis becomes available.
```

ただし、Redis 接続失敗ログは `redis.failure-log-cooldown-seconds` で抑制し、heartbeat のたびに大量出力しない。

### 5.2 Redis 接続タイムアウト設定

```yaml
redis:
  connection-timeout-millis: 1000
  socket-timeout-millis: 1000
  failure-log-cooldown-seconds: 60
```

仕様:

- Redis 接続は短い timeout を必ず設定する。
- `connection-timeout-millis` は Redis への接続確立 timeout。
- `socket-timeout-millis` は Redis 操作の read/write timeout。
- Redis 障害時に Velocity のログイン処理や backend 移動処理を待たせないため、Redis 操作を Velocity の通常処理スレッド上で同期実行しない。
- Redis 操作は heartbeat scheduler または command 用の非同期タスク内で実行する。
- Redis が復旧した場合、次回 heartbeat、次回 command 実行、または `/vstats reload` 後の再初期化で自動的に復帰してよい。

### 5.3 public node の例

```yaml
node:
  id: "prx01"
  group: "public"
```

### 5.4 staff node の例 任意

```yaml
node:
  id: "staff"
  group: "staff"
```

### 5.5 node.id のルール

staff node は任意である。public node だけの構成でも、この Plugin は正常に動作する。

- 全 Velocity で一意にする。
- public node は `prx01`, `prx02`, `prx03` のようにする。
- staff 専用 node を用意する場合は `staff` を推奨する。staff node は必須ではない。
- Redis key に使うため、半角英数字・ハイフン・アンダースコアのみ推奨。
- 表示名も `node.id` をそのまま使用する。`display-name` 項目は設けない。


### 5.6 backend 設定

```yaml
backend:
  enabled: true
  unassigned-name: "unassigned"
```

仕様:

- `backend.enabled=true` の場合、各プレイヤーの接続先 backend server 名を Redis に保存する。
- backend server 名は Velocity の registered server 名を使う。
- `backend.enabled=true` の場合、各 Velocity は自分が知っている registered server を0人でも `backends` に保存する。これにより `/vstats` と `/vstats servers` は0人の backend server も表示できる。
- `backend.enabled=true` でプレイヤーが backend 未接続の場合は `backend.unassigned-name` の値で集計する。
- `backend.enabled=false` の場合、backend 接続先は保存せず、`backends` も書き込まない。
- Fabric Server が1台の構成でもこの設定は有効のままでよい。
- 複数 Fabric Server 構成へ移行しても config の互換性を維持する。

---

### 5.7 `/vstats reload` による設定再読み込み

Velocity を再起動せずに `config.yml` の変更を反映するため、`/vstats reload` を実装する。

reload で反映する項目:

- `redis.*`
  - host / port / password / database / key-prefix
  - connection timeout / socket timeout
  - failure log cooldown
- `node.*`
  - id
  - group
- `heartbeat.*`
  - interval-seconds
  - ttl-seconds
- `backend.*`
  - enabled
  - unassigned-name
- `command.*`
  - primary
  - snapshot-cache-millis
  - player-list-limit
  - 原則は `vstats` のまま使用する。
  - 将来的に変更可能にする場合も、既存の `/vstats reload` が実行不能にならないようにする。
- `permissions.*`
  - view / staff / list / reload

reload 処理仕様:

1. `/vstats reload` 実行時、権限 `vstats.reload` を確認する。
2. 権限があれば、実行者へ `[vstats] Reloading config...` を即時返信する。
3. config ファイル読み込み、検証、RedisManager 再初期化、heartbeat reschedule は非同期タスクで行う。
4. 新しい config が正常に読み込めた場合のみ、有効設定を差し替える。
5. Redis 接続確認に失敗しても、新しい config 自体が正常なら reload は成功扱いにする。
6. Redis 接続不可の場合は `degraded=true` とし、heartbeat 書き込みと stats 読み取りだけを一時的に失敗扱いにする。
7. config 構文エラー、必須項目不正、`node.group` が `public` / `staff` 以外などの場合は reload 失敗とし、現在有効な config を維持する。ただし cluster 内に staff node が存在しないことは正常構成として扱う。
8. heartbeat interval が変わった場合は、既存 heartbeat task を安全に cancel してから新しい interval で reschedule する。
9. reload 完了後、実行者へ成功または失敗メッセージを送る。

非同期・フェイルセーフ要件:

- `/vstats reload` 実行スレッドで Redis へ同期接続しない。
- Redis の接続確認、Jedis pool 作成、ping、snapshot read/write は command 実行スレッドで行わない。
- Redis timeout は reload 時にも `connection-timeout-millis` / `socket-timeout-millis` を使う。
- Redis 接続不可でも Velocity の起動、ログイン、backend 移動、既存プレイヤーの通信に影響させない。
- reload 中に `/vstats` や heartbeat が実行されても、古い有効 config または新しい有効 config のどちらか一貫した snapshot を使う。
- config 差し替えは `AtomicReference<RuntimeConfig>` のような方式で行い、中途半端な状態を外部に見せない。

推奨内部状態:

```java
AtomicReference<RuntimeConfig> currentConfig;
AtomicReference<RedisManager> redisManager;
AtomicBoolean reloadInProgress;
```

`reloadInProgress=true` の間に再度 `/vstats reload` が実行された場合は、二重実行を避けるため以下を表示する。

```text
[vstats] Reload is already in progress.
```

## 6. Redisキー設計

Redis key prefix は config の `redis.key-prefix` を使う。  
デフォルトは `vstats`。

### 6.1 node meta

```text
vstats:nodes:{nodeId}:meta
```

型: Hash

Fields:

| field | 内容 | 例 |
|---|---|---|
| `id` | node id | `prx01` |
| `group` | `public` or `staff` | `public` |
| `player_count` | 接続人数 | `13` |
| `updated_at` | epoch milliseconds | `1778270000000` |

TTL: `heartbeat.ttl-seconds`

### 6.2 node players

```text
vstats:nodes:{nodeId}:players
```

型: Set

Values:

```text
playerA
playerB
playerC
```

TTL: `heartbeat.ttl-seconds`



### 6.3 node player server map

```text
vstats:nodes:{nodeId}:player_servers
```

型: Hash

Fields:

| field | 内容 | 例 |
|---|---|---|
| `playerName` | backend server 名 | `lobby` |

例:

```text
playerA -> lobby
playerB -> main
playerC -> game01
```

TTL: `heartbeat.ttl-seconds`

### 6.4 node backend counts

```text
vstats:nodes:{nodeId}:backends
```

型: Hash

Fields:

| field | 内容 | 例 |
|---|---|---|
| `backendServerName` | その node 経由で該当 backend server に接続中の人数 | `13` |

例:

```text
lobby -> 4
main -> 7
game01 -> 2
```

TTL: `heartbeat.ttl-seconds`

### 6.5 active node index

```text
vstats:nodes
```

型: Set

Values:

```text
prx01
prx02
prx03
staff
```

上記は staff node が存在する場合の例である。staff node が存在しない場合、`vstats:nodes` に `staff` は含まれなくてよい。

注意:

- `vstats:nodes` 自体にも `heartbeat.ttl-seconds` の TTL を付け、active node の heartbeat ごとに延長する。
- 集計時に各 `vstats:nodes:{nodeId}:meta` の存在確認を行う。
- meta が存在しない node は inactive として無視する。
- inactive node は heartbeat / shutdown / 集計時に必要に応じて `vstats:nodes` から削除してよい。

---


## 7. Redis障害時のフェイルセーフ仕様

Redis は集計用の共有ストアであり、Velocity の必須起動条件ではない。Redis に接続できない、認証に失敗する、timeout する、または config が初期テンプレートのままでも、Velocity の通常動作を停止してはならない。

### 7.1 起動時の扱い

- Plugin 初期化時に Redis 接続確認を行ってもよいが、失敗しても例外を外へ投げて Velocity 起動を失敗させない。
- Redis 接続失敗時も `/vstats` コマンド登録は行う。
- Redis 接続失敗時は heartbeat 書き込みだけを skip する。
- Redis 接続失敗時の状態を内部的に `redisAvailable=false` または `degraded=true` として保持してよい。
- Redis 復旧時は、次回 heartbeat で通常状態へ戻す。

### 7.2 ラグ防止

Redis 操作は以下の処理をブロックしてはならない。

- プレイヤーログイン
- backend server への接続・移動
- チャット
- コマンド補完
- Velocity の ProxyInitialize / ProxyShutdown の主要処理

実装ルール:

- Redis の read/write は Velocity の通常処理スレッドで同期実行しない。
- heartbeat は Velocity Scheduler の非同期タスクとして実行する。
- `/vstats` 実行時の Redis read も非同期タスクに投げ、取得後に CommandSource へ返信する。
- 非同期タスク内でも Redis timeout は `redis.connection-timeout-millis` / `redis.socket-timeout-millis` を必ず使う。
- Redis 操作に失敗した場合は、その1回の stats 更新または command 表示だけを失敗扱いにする。

### 7.3 `/vstats` 実行時に Redis が使えない場合

Redis snapshot を取得できない場合、各コマンドは以下を表示する。

```text
[stats] Redis connection error.
```

このエラーは stats 機能のエラーであり、Velocity 本体のエラーではない。

### 7.4 ログ抑制

Redis 障害中に heartbeat が `interval-seconds` ごとに失敗しても、毎回 stack trace を出力しない。

仕様:

- 同種の Redis 接続失敗ログは `redis.failure-log-cooldown-seconds` に1回までに抑制する。
- debug レベルがない限り、毎 heartbeat ごとの stack trace は出さない。
- 初回失敗時は warn ログでよい。
- 復旧時は info ログを1回だけ出してよい。

例:

```text
[velocity-cluster-stats] Redis connection failed: Connection refused. Stats are temporarily unavailable.
[velocity-cluster-stats] Redis connection restored.
```

### 7.5 Redis 未接続時の内部状態

Redis に接続できない場合、Plugin は以下のように振る舞う。

| 処理 | Redis接続不可時の挙動 |
|---|---|
| Plugin 起動 | 継続する |
| `/vstats` コマンド登録 | 継続する |
| heartbeat | その回だけ skip する |
| `/vstats` | `[stats] Redis connection error.` を表示する |
| `/vstats reload` | Redis接続不可でも config 再読み込みは可能。Redisのみ degraded として扱う |
| プレイヤーログイン | 影響させない |
| backend 移動 | 影響させない |
| Velocity 停止 | 通常通り停止する |

## 8. heartbeat仕様

各 Velocity は `heartbeat.interval-seconds` ごとに Redis へ以下を行う。

1. `vstats:nodes` に自分の `node.id` を追加する。
2. `vstats:nodes:{nodeId}:meta` を更新する。
3. `vstats:nodes:{nodeId}:players` を現在のプレイヤー名 Set で置き換える。
4. `vstats:nodes:{nodeId}:player_servers` を現在のプレイヤー名 → backend server 名の Hash で置き換える。
5. `vstats:nodes:{nodeId}:backends` を backend server 別人数の Hash で置き換える。
6. meta / players / player_servers / backends / nodes index に TTL を設定する。
7. meta が存在しない stale node id を `vstats:nodes` から削除する。

疑似コード:

```java
void publishHeartbeat() {
    String nodeId = config.node.id;

    Map<String, String> playerServers = new HashMap<>();
    Map<String, Integer> backendCounts = new HashMap<>();
    List<String> playerNames = new ArrayList<>();

    if (config.backend.enabled) {
        for (RegisteredServer server : proxyServer.getAllServers()) {
            backendCounts.put(server.getServerInfo().getName(), 0);
        }
    }

    for (Player player : proxyServer.getAllPlayers()) {
        String playerName = player.getUsername();
        playerNames.add(playerName);

        if (config.backend.enabled) {
            String backendName = player.getCurrentServer()
                .map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse(config.backend.unassignedName);
            playerServers.put(playerName, backendName);
            backendCounts.put(backendName, backendCounts.getOrDefault(backendName, 0) + 1);
        }
    }

    playerNames.sort(String.CASE_INSENSITIVE_ORDER);

    Transaction tx = redis.multi();
    tx.sadd("vstats:nodes", nodeId);

    tx.hset("vstats:nodes:" + nodeId + ":meta", Map.of(
        "id", nodeId,
        "group", config.node.group,
        "player_count", String.valueOf(playerNames.size()),
        "updated_at", String.valueOf(System.currentTimeMillis())
    ));

    tx.del("vstats:nodes:" + nodeId + ":players");
    if (!playerNames.isEmpty()) {
        tx.sadd("vstats:nodes:" + nodeId + ":players", playerNames.toArray(String[]::new));
    }

    tx.del("vstats:nodes:" + nodeId + ":player_servers");
    if (!playerServers.isEmpty()) {
        tx.hset("vstats:nodes:" + nodeId + ":player_servers", playerServers);
    }

    tx.del("vstats:nodes:" + nodeId + ":backends");
    if (!backendCounts.isEmpty()) {
        Map<String, String> backendCountStrings = backendCounts.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        tx.hset("vstats:nodes:" + nodeId + ":backends", backendCountStrings);
    }

    tx.expire("vstats:nodes:" + nodeId + ":meta", ttlSeconds);
    tx.expire("vstats:nodes:" + nodeId + ":players", ttlSeconds);
    tx.expire("vstats:nodes:" + nodeId + ":player_servers", ttlSeconds);
    tx.expire("vstats:nodes:" + nodeId + ":backends", ttlSeconds);
    tx.expire("vstats:nodes", ttlSeconds);
    tx.exec();

    for (String indexedNodeId : redis.smembers("vstats:nodes")) {
        if (!indexedNodeId.equals(nodeId) && !redis.exists("vstats:nodes:" + indexedNodeId + ":meta")) {
            redis.del(
                "vstats:nodes:" + indexedNodeId + ":meta",
                "vstats:nodes:" + indexedNodeId + ":players",
                "vstats:nodes:" + indexedNodeId + ":player_servers",
                "vstats:nodes:" + indexedNodeId + ":backends"
            );
            redis.srem("vstats:nodes", indexedNodeId);
        }
    }
}
```

---

## 9. 集計仕様

### 9.1 node snapshot

コマンド実行時に以下を取得する。

1. `SMEMBERS vstats:nodes`
2. 各 node の `meta` を取得
3. meta が存在しない node は inactive として除外
4. 各 node の `players` Set を取得
5. 各 node の `backends` Hash を取得
6. group ごとに分類
7. backend server ごとに public / staff 別人数を集計

内部モデル例:

`displayName` は持たせず、表示には `id` を使用する。

```java
record NodeSnapshot(
    String id,
    String group,
    int playerCount,
    List<String> players,
    Map<String, Integer> backendCounts,
    long updatedAt
) {}

record ServerGroupCount(
    String serverName,
    int publicPlayers,
    int staffPlayers
) {
    int totalPlayers() {
        return publicPlayers + staffPlayers;
    }
}
```

### 9.2 集計ルール

- active node 数 = meta が存在する node 数。
- public node 数 = `group=public` の active node 数。
- staff node 数 = `group=staff` の active node 数。staff node が存在しない場合は 0。
- public players = public node の `player_count` 合計。
- staff players = staff node の `player_count` 合計。staff node が存在しない場合は 0。
- total players = public players + staff players。
- backend public players = `group=public` の node の `backendCounts` を backend server 名ごとに合計する。
- backend staff players = `group=staff` の node の `backendCounts` を backend server 名ごとに合計する。staff node が存在しない場合は各 backend server で 0。
- backend total players = backend public players + backend staff players。
- public 表示は node id 昇順。
- staff 表示は node 個別表示せず group 合計のみ。
- `[Servers]` 表示は backend server 名昇順。
- backend `Total` は `Total: X players, Public Y, Staff Z` 形式で表示する。
- backend 行の区切りは `, ` とする。


### 9.3 backend server 集計ルール

各 Velocity は、自分に接続しているプレイヤーが現在どの backend server にいるかを Redis に保存する。
コマンド実行側は全 node の backend counts を合算し、cluster 全体の backend server 別人数を作る。

例:

| node | group | lobby | main | game01 |
|---|---|---:|---:|---:|
| prx01 | public | 3 | 8 | 2 |
| prx02 | public | 4 | 9 | 3 |
| prx03 | public | 3 | 6 | 4 |
| staff | staff | 0 | 2 | 1 |
| **Public Total** |  | **10** | **23** | **9** |
| **Staff Total** |  | **0** | **2** | **1** |
| **Total** |  | **10** | **25** | **10** |

表示では以下のように合算後の結果だけを出す。

```text
[Servers]
game01: Public 9, Staff 1
lobby: Public 10, Staff 0
main: Public 23, Staff 2
Total: 45 players, Public 42, Staff 3
```

staff node が存在しない場合、`/vstats` の `[Staff]` ブロックは省略する。
`[Servers]` も staff 列を表示せず、public のみの簡潔な形式にする。

```text
[Servers]
game01: Public 9
lobby: Public 10
main: Public 23
Total: 42 players
```

注意:

- staff 専用 Velocity 経由のプレイヤーも `[Servers]` の backend server 別人数には含める。
- `[Servers]` では staff 専用 Velocity 経由の人数を `Staff` として分けて表示する。
- `/vstats` の `Total Players` と `[Servers] Total` の players は一致することを受け入れ条件に含める。
- 一致しない場合は、実装バグまたは heartbeat 中の一時的な不整合として扱う。

---

## 10. 権限仕様

本Pluginは、権限チェックを Velocity の Permission API 経由で行う。  
LuckPerms を Velocity 側に導入している場合は、LuckPerms の権限ノードで制御する。

実装上は LuckPerms API に直接依存しない。  
CommandSource に対して `getPermissionValue(...)` を呼び出し、Velocity 側の権限プロバイダに判定を委譲する。
`Tristate.FALSE` の場合だけ拒否し、`Tristate.TRUE` と `Tristate.UNDEFINED` は許可する。
これにより LuckPerms などで権限設定がない場合でも、デフォルトではコマンドを利用できる。

例:

```java
source.getPermissionValue(config.permissions().view()) != Tristate.FALSE
source.getPermissionValue(config.permissions().staff()) != Tristate.FALSE
source.getPermissionValue(config.permissions().list()) != Tristate.FALSE
source.getPermissionValue(config.permissions().reload()) != Tristate.FALSE
```

理由:

- LuckPerms 以外の Velocity 対応 permission plugin にも対応しやすくするため。
- LuckPerms が未導入でも Plugin 自体は起動できるようにするため。
- LuckPerms などで未設定の場合は初期状態で利用可能にし、必要な場合だけ明示的に拒否できるようにするため。
- 権限管理は Plugin 独自実装ではなく、既存の Velocity 権限システムへ委譲するため。

Console からの実行は管理者操作として扱い、権限チェックを通過させる。  
Player からの実行のみ、以下の権限ノードを確認する。

| 権限 | 内容 |
|---|---|
| `vstats.view` | `/vstats`, `/vstats public`, `/vstats servers` を実行できる |
| `vstats.staff` | `/vstats staff`, `/vstats list staff`, staff node の list を実行できる |
| `vstats.list` | `/vstats list`, `/vstats list <nodeId>` を実行できる |
| `vstats.reload` | `/vstats reload` を実行できる |

`/vstats help` は権限なしで実行でき、実行者が利用可能なコマンドのみ表示する。
未定義の permission node は許可扱いのため、権限設定がない環境では全コマンドを表示する。

LuckPerms 設定例:

```text
/lpv group admin permission set vstats.view true
/lpv group admin permission set vstats.staff true
/lpv group admin permission set vstats.list true
/lpv group admin permission set vstats.reload true

/lpv group moderator permission set vstats.view true
/lpv group moderator permission set vstats.list true
```

一般ユーザーに人数だけ見せる場合:

```text
/lpv group default permission set vstats.view true
```

---

## 11. コマンドパース仕様

### 11.1 `/vstats`

引数なし。

処理:

1. `vstats.view` を確認。
2. Redis snapshot を取得。
3. 全体サマリを表示。

### 11.2 `/vstats public`

処理:

1. `vstats.view` を確認。
2. Redis snapshot を取得。
3. public block のみ表示。

### 11.3 `/vstats staff`

処理:

1. `vstats.staff` を確認。
2. Redis snapshot を取得。
3. staff block のみ表示。staff node が存在しない場合も `staff: 0 players` と表示する。通常の `/vstats` では staff node が存在しない場合に `[Staff]` ブロックを省略する。

### 11.4 `/vstats servers`

処理:

1. `vstats.view` を確認。
2. Redis snapshot を取得。
3. servers block のみ表示。

### 11.5 `/vstats list`

処理:

1. `vstats.list` を確認。
2. Redis snapshot を取得。
3. 実行者が `vstats.staff` を持つ場合は public + staff の全プレイヤーを表示。
4. 実行者が `vstats.staff` を持たない場合は public のプレイヤーのみ表示。

### 11.6 `/vstats list <target>`

`target` の扱い:

- `public` の場合: public group alias。
- `staff` の場合: staff group alias。
- それ以外の場合: node id。

処理:

1. `vstats.list` を確認。
2. `target=staff` の場合は `vstats.staff` も確認。`target=public` の場合は `vstats.list` のみでよい。
3. Redis snapshot を取得。
4. 対象プレイヤー名を抽出。public / staff node が存在しない場合は空リストとして扱い、node not found にはしない。
5. whitelist 風フォーマットで表示。

### 11.7 `/vstats reload`

処理:

1. `vstats.reload` を確認。
2. `reloadInProgress` が true の場合は `[vstats] Reload is already in progress.` を表示して終了。
3. `[vstats] Reloading config...` を即時表示。
4. 非同期タスクで `config.yml` を読み込む。
5. config を検証する。
6. 新しい `RedisManager` を短い timeout 付きで初期化する。
7. Redis 接続確認に失敗しても、config が正常なら有効設定を差し替える。
8. heartbeat interval が変更された場合は heartbeat task を reschedule する。
9. 成功・Redis unavailable・失敗のいずれかを実行者へ表示する。

Redis 接続不可は reload コマンドの処理を長時間待たせない。  
Redis 側の失敗は timeout 内で打ち切り、Velocity の通常処理へ影響させない。

---

## 12. エラー表示仕様

### 12.1 Redisに接続できない

```text
[stats] Redis connection error.
```

### 12.2 権限がない

```text
You do not have permission to use this command.
```

staff 閲覧権限がない場合:

```text
You do not have permission to view staff stats.
```

staff list 閲覧権限がない場合:

```text
You do not have permission to view staff player list.
```

### 12.3 node が存在しない

```text
Velocity node not found: prx99
```

### 12.4 不明な引数

```text
Unknown subcommand. Usage: /vstats [public|staff|servers|list|reload|help]
```

`list` の使い方:

```text
Usage: /vstats list [nodeId|public|staff]
```

---

## 13. 実装クラス構成案

```text
src/main/java/com/example/vstats/
  VelocityClusterStatsPlugin.java
  config/
    PluginConfig.java
    ConfigLoader.java
  redis/
    RedisManager.java
    RedisKeys.java
    HeartbeatPublisher.java
    StatsRepository.java
  model/
    NodeSnapshot.java
    ClusterSnapshot.java
  command/
    VStatsCommand.java
    ReloadService.java
    MessageFormatter.java
  util/
    PlayerNameSorter.java
```

### 13.1 `VelocityClusterStatsPlugin`

責務:

- Plugin 初期化
- config 自動生成
- config 読み込み
- RedisManager 初期化
- heartbeat scheduler 起動
- `/vstats` コマンド登録
- `/vstats reload` を含む subcommand 登録
- Redis 接続失敗時も Velocity 起動を失敗させない

### 13.2 `ConfigLoader`

責務:

- `plugins/velocity-cluster-stats/` ディレクトリ作成
- `config.yml` が存在しない場合のテンプレート自動生成
- 既存 config の非破壊読み込み
- 不正 config の場合も可能な範囲でデフォルト値を補完し、致命的例外にしない
- reload 用に「読み込み + 検証のみ」を行い、成功時だけ RuntimeConfig として返す

### 13.3 `RedisManager`

責務:

- Redis connection pool または connection factory の管理
- connection timeout / socket timeout の適用
- Redis 操作失敗時の例外吸収
- reload 時に古い connection pool を安全に close し、新しい設定へ差し替える
- `redisAvailable` / `degraded` 状態の管理
- 失敗ログの cooldown 制御

### 13.4 `HeartbeatPublisher`

責務:

- 現在の Velocity に接続しているプレイヤー名一覧を収集
- Redis へ meta / players / player_servers / backends / node index を書き込む
- TTL を更新
- Redis 接続失敗時はその回の heartbeat だけ skip し、Velocity 動作へ影響させない

### 13.5 `StatsRepository`

責務:

- Redis から active node snapshot を取得
- public / staff に分類
- backend server 別人数を合算
- inactive node の除外

### 13.6 `VStatsCommand`

責務:

- `/vstats` の引数パース
- 権限チェック
- Redis read を非同期タスクで実行する
- Redis read 失敗時に `[stats] Redis connection error.` を表示する
- `/vstats reload` で config reload を非同期実行する
- `MessageFormatter` を使った表示

### 13.7 `ReloadService`

責務:

- `/vstats reload` の実処理を非同期で実行する。
- 二重 reload を防ぐ。
- config 読み込み・検証を行う。
- RedisManager を新しい config で再初期化する。
- heartbeat task を必要に応じて reschedule する。
- reload 成功、Redis unavailable、reload 失敗を実行者へ通知する。

実装方針:

```java
if (!reloadInProgress.compareAndSet(false, true)) {
    source.sendMessage(Component.text("[vstats] Reload is already in progress."));
    return;
}

source.sendMessage(Component.text("[vstats] Reloading config..."));

scheduler.buildTask(plugin, () -> {
    try {
        ReloadResult result = reloadService.reload();
        source.sendMessage(result.message());
    } finally {
        reloadInProgress.set(false);
    }
}).schedule();
```

注意:

- `reloadService.reload()` の中で Redis へ接続する場合も、必ず timeout を使う。
- Redis 接続失敗は `ReloadResult.REDIS_UNAVAILABLE` として扱い、Plugin を停止しない。
- config 検証失敗時は `ReloadResult.FAILED` とし、古い RuntimeConfig を維持する。

### 13.8 `MessageFormatter`

責務:

- `/vstats` 表示 `Component` 生成
- `[Servers]` 表示 `Component` 生成
- `/vstats servers` 表示 `Component` 生成
- `/vstats public` 表示 `Component` 生成
- `/vstats staff` 表示 `Component` 生成
- `/vstats list` 表示 `Component` 生成
- `/vstats help` 表示 `Component` 生成
- テストやログ確認用に plain text 変換可能な表示を維持する

---

## 14. MessageFormatter 仕様

### 14.0 カラーリング

コマンド表示は Adventure `Component` で生成し、以下の配色を固定する。

| 対象 | 色 |
|---|---|
| `[Stats]`, `[Servers]` | `NamedTextColor.GOLD` |
| `[Public]`, `Public` label | `NamedTextColor.GREEN` |
| `[Staff]`, `Staff` label | `NamedTextColor.LIGHT_PURPLE` |
| node 名、server 名、player 名 | `NamedTextColor.WHITE` |
| 数値 | `NamedTextColor.AQUA` |
| 通常ラベル、区切り、`players` | `NamedTextColor.GRAY` |
| 権限エラー、Redis エラー、不明コマンド | `NamedTextColor.RED` |
| reload 中、reload 二重実行、usage | `NamedTextColor.YELLOW` |
| reload 成功 | `NamedTextColor.GREEN` |

plain text として読んだ場合の表示内容は、以下の表示形式と一致させる。

### 14.1 全体表示

```java
String formatRoot(ClusterSnapshot snapshot) {
    return """
        ========================================
        [Stats]
        Active Velocity Nodes: %d,  public=%d / staff=%d
        Total Players: %d

        [Public]
        %s
        Total: %d players

        [Staff]
        staff: %d players

        [Servers]
        %s
        Total: %d players, Public %d, Staff %d
        """;
}

String formatServers(ClusterSnapshot snapshot) {
    return """
        [Servers]
        %s
        Total: %d players, Public %d, Staff %d
        """;
}
```

### 14.2 list 表示

```java
String formatPlayerList(List<String> players) {
    if (players.isEmpty()) {
        return "There are 0 player(s):";
    }
    return "There are " + players.size() + " player(s): " + String.join(", ", players);
}
```

---

## 15. Codex 実装指示

Codex には以下の方針で実装させる。

```text
Velocity 3.5.0-SNAPSHOT 向けの Java Plugin を作成してください。
Fabric Server 側 Plugin は作成しません。
Fabric Server は1台構成から複数台構成へ増える可能性があります。
staff 専用 Velocity node は任意です。public node だけの構成でも `/vstats`、`/vstats staff`、`/vstats servers`、`/vstats list staff` がエラーなく動作するようにしてください。
Redis を共有ストアとして使い、各 Velocity が heartbeat で自分の node 状態、プレイヤー名一覧、各プレイヤーの接続先 backend server 名、backend server 別人数を書き込んでください。

実装するコマンドは以下だけです。
- /vstats
- /vstats public
- /vstats staff
- /vstats servers
- /vstats list
- /vstats list public
- /vstats list <nodeId>
- /vstats list staff
- /vstats reload

/vstats の表示は次の形式に固定してください。

========================================
[Stats]
Active Velocity Nodes: 4,  public=3 / staff=1
Total Players: 45

[Public]
prx01: 13 players
prx02: 16 players
prx03: 13 players
Total: 42 players

[Staff]
staff: 3 players

[Servers]
game01: Public 9, Staff 1
lobby: Public 10, Staff 0
main: Public 23, Staff 2
Total: 45 players, Public 42, Staff 3

/vstats public は [Public] ブロックのみ表示してください。
/vstats staff は [Staff] ブロックのみ表示してください。staff node が存在しない場合でも `staff: 0 players` と表示してください。
/vstats servers は [Servers] ブロックのみ表示してください。staff node が存在する場合は `serverName: Public X, Staff Y` と `Total: X players, Public Y, Staff Z` の形式で表示してください。区切りは `, ` を使ってください。staff node がない構成では `serverName: Public X` と `Total: X players` の形式で表示し、`Staff 0` は表示しないでください。
/vstats list 系は whitelist コマンド風に `There are X player(s): xxx, yyy, zzzz` の形式で表示してください。`/vstats list public` は public group のプレイヤーのみ表示してください。`/vstats list staff` は staff node が存在しない場合でも `There are 0 player(s):` と表示してください。

/vstats nodes, groups, players, json, redis, help, summary などは実装しないでください。
/vstats list <backendServer> のような backend server 指定 list も実装しないでください。

/vstats reload は Velocity を再起動せずに config.yml を再読み込みしてください。reload では redis設定、node設定、heartbeat設定、backend設定、permissions設定を反映してください。reload 処理は非同期で行い、Redis接続不可でも Velocity の通常処理をブロックしないでください。Redisに接続できない場合でも config が正常なら reload は成功扱いとし、stats機能のみ degraded としてください。

config が存在しない場合は plugins/velocity-cluster-stats/config.yml を自動生成してください。既存 config は上書きしないでください。

Redis 接続不可は致命的エラーにしないでください。Redis が落ちている、未設定、認証失敗、timeout のいずれでも Velocity の起動・ログイン・backend 移動を止めず、stats 機能のみ一時的に利用不可にしてください。

Redis read/write は Velocity の通常処理スレッドで同期実行しないでください。heartbeat と /vstats の Redis 読み取りは非同期タスクとして実装し、timeout とログ抑制を必ず入れてください。
```

---

## 16. 受け入れ条件

| No | 条件 | 期待結果 |
|---|---|---|
| 1 | public node 3台、staff node 1台が heartbeat を送信 | `/vstats` で `Active Velocity Nodes: 4,  public=3 / staff=1` と表示される |
| 2 | public 合計42人、staff合計3人 | `/vstats` で `Total Players: 45` と表示される |
| 3 | `/vstats public` 実行 | `[Public]` ブロックのみ表示される |
| 4 | `/vstats staff` 実行 | `[Staff]` ブロックのみ表示される |
| 5 | `/vstats list` 実行 | `There are X player(s): ...` 形式で表示される |
| 6 | `/vstats list public` 実行 | public group に接続中のユーザー名だけ表示される |
| 7 | `/vstats list prx01` 実行 | prx01 に接続中のユーザー名だけ表示される |
| 8 | `/vstats list staff` 実行 | staff group に接続中のユーザー名だけ表示される |
| 9 | 初回起動時に `config.yml` が存在しない | `plugins/velocity-cluster-stats/config.yml` が自動生成され、Velocity 起動は継続する |
| 10 | Redis接続不可 | `/vstats` で `[stats] Redis connection error.` と表示され、Velocity のログイン・backend 移動は影響を受けない |
| 11 | Redis停止中に heartbeat が複数回失敗 | ログが cooldown され、毎回 stack trace を出さない |
| 12 | Redis復旧後 | 次回 heartbeat または command 実行で自動復帰する |
| 13 | TTL切れnodeが存在 | active nodeとして扱わない |
| 14 | `/vstats list prx99` 実行 | `Velocity node not found: prx99` と表示される |
| 15 | staff権限なしで `/vstats staff` | 権限エラーを表示する |
| 16 | `/vstats reload` 実行 | config が非同期で再読み込みされ、成功メッセージを表示する |
| 17 | Redis停止中に `/vstats reload` 実行 | Velocity のログイン・backend 移動に影響せず、Redis unavailable を表示する |
| 18 | 不正configで `/vstats reload` 実行 | reload 失敗を表示し、現在有効な設定を維持する |
| 19 | reload中に再度 `/vstats reload` 実行 | `[vstats] Reload is already in progress.` を表示する |
| 20 | Fabric Server が lobby/main/game01 の3台 | `/vstats` に `[Servers]` ブロックが表示される |
| 21 | backend server 別人数が public/staff 別に存在 | `[Servers]` に `game01: Public 9, Staff 1`, `lobby: Public 10, Staff 0`, `main: Public 23, Staff 2` が表示される |
| 22 | `/vstats servers` 実行 | `[Servers]` ブロックのみ表示される |
| 23 | server移動中のプレイヤーがいる | `unassigned: Public X, Staff Y` として `[Servers]` に表示される |
| 24 | `/vstats public` 実行 | backend server 別表示は出さず `[Public]` ブロックのみ表示される |
| 25 | `/vstats staff` 実行 | backend server 別表示は出さず `[Staff]` ブロックのみ表示される |
| 26 | staff node が存在せず public node 3台のみ | `/vstats` で `Active Velocity Nodes: 3,  public=3` と表示され、`[Staff]` ブロックは表示されない |
| 27 | staff node が存在しない状態で `/vstats staff` 実行 | `[Staff]` と `staff: 0 players` が表示される |
| 28 | staff node が存在しない状態で `/vstats list staff` 実行 | `There are 0 player(s):` と表示され、`Velocity node not found` にはならない |
| 29 | staff node が存在しない状態で `/vstats servers` 実行 | 各 server 行は `serverName: Public X`、Total は `Total: X players` と表示され、`Staff 0` は表示されない |
| 30 | Velocity に registered server が存在し、その server に接続中プレイヤーが0人 | `/vstats` と `/vstats servers` に `serverName: Public 0` または `serverName: Public 0, Staff 0` と表示される |

---

## 17. 補足

`/vstats` は Velocity Proxy Command として実装する。  
そのため、プレイヤーが Fabric Server に接続中でも、ゲーム内チャットから `/vstats` を実行できる。

ただし、Fabric Server の物理コンソールや RCON から `/vstats` を実行しても、この Velocity Plugin のコマンドは実行されない。

staff 専用 Velocity をまだ用意していない段階でも、public node だけで Plugin を導入できる。後から staff node を追加した場合は、その node の `config.yml` で `node.group: "staff"` を設定し、`/vstats reload` または Velocity Plugin の再読込相当の操作で反映する。


---

## 18. 複数 Fabric Server 構成への移行メモ

Fabric Server を複数台にする場合でも、Fabric 側 Plugin は不要とする。
Velocity は各プレイヤーの current server を把握できるため、Velocity Plugin の heartbeat だけで backend server 別人数を集計できる。

想定例:

```text
Velocity servers:
  lobby  -> 192.168.103.2:30010
  main   -> 192.168.103.3:30011
  game01 -> 192.168.103.4:30012
```

この場合、Redis には以下のように保存される。

```text
vstats:nodes:prx01:backends
  lobby = 3
  main = 8
  game01 = 2

vstats:nodes:prx02:backends
  lobby = 4
  main = 9
  game01 = 3
```

`/vstats` および `/vstats servers` 実行時は全 Velocity node の `backends` を、node group ごとに合算して `[Servers]` に表示する。

実装上の注意:

- backend server 名は Velocity の registered server 名を使い、IPアドレスやポートは表示しない。
- backend server が停止していても Velocity に registered server として定義されていれば、0人として表示する。
- `unassigned` はログイン直後や移動中など、未割り当てプレイヤーが存在する場合のみ表示する。
- `/vstats servers` は backend server 別人数のみを表示する。
- `list` コマンドはユーザー名一覧だけを目的とし、backend server 指定 list は実装しない。
