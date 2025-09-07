# SignWarpX [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.4+-green.svg)](https://www.minecraft.net/) [![Paper](https://img.shields.io/badge/Paper-Required-blue.svg)](https://papermc.io/)

##  簡介

SignWarpX 是一款專為 Minecraft Paper 伺服器設計的傳送插件，讓玩家能夠透過簡單的告示牌系統建立個人、群組或公共的傳送錨點。無論是建立城市間的交通網絡，或是打造專屬的探險據點，都能輕鬆實現！

## 功能特色

###  核心功能
- **簡易告示牌系統** - 透過放置告示牌輕鬆建立傳送錨點
- **權限管理** - 支援公共、私人與群組傳送錨點
- **邀請系統** - 允許分享私人傳送錨點給特定玩家
- **群組管理** - 支援群組傳送錨點與成員管理
- **管理介面** - 提供 GUI 管理所有傳送錨點
- **即時傳送** - 支援多種傳送狀態與載具

###  進階傳送支援
- **騎乘傳送** - 騎馬、豬等載具時一同傳送
- **船隻傳送** - 包含船上的實體（動物、怪物）一起傳送
- **韁繩傳送** - 手持韁繩時連同綁定實體一起傳送
###  自訂化選項
- **訊息自訂** - 完全可自訂的插件訊息，支援多種佔位符
- **冷卻系統** - 可設定傳送延遲與冷卻時間
- **物品消耗** - 可設定傳送所需物品（預設：終界珍珠）
- **傳送錨點數量自訂** - 可設定每人最多建立傳送錨點數量

###  群組管理系統
- **群組建立** - 建立私人群組管理多個傳送錨點
- **成員管理** - 邀請/移除群組成員
- **批量管理** - 一次管理多個傳送錨點的存取權限
## 安裝說明

### 系統需求
- **Minecraft 版本**: 1.21 或更高版本
- **伺服器軟體**: Paper（推薦）或其他基於 Paper 的核心
- **Java 版本**: Java 21 或更高版本

### API 版本支援

SignWarpX 支援多版本 API 自動切換：
- **1.21.6 及以上版本** → 使用 1.21.6 API
- **1.21-1.21.5** → 使用 1.21 API

插件會自動檢測伺服器版本並選擇對應的 API，無需手動配置。

### 安裝步驟
1. 下載最新版本的 [SignWarpX.jar](https://modrinth.com/plugin/signwarpx/versions) 檔案
2. 將檔案放入伺服器的 `plugins` 資料夾
3. 重新啟動伺服器
4. 編輯 `config.yml` 來自訂設定（可選）

##  使用方式

### 建立傳送錨點 (WarpTarget)
1. **放置告示牌** 在您想要設置為傳送目的地的位置
2. **編輯告示牌內容**：
    ```
    第一行: [WarpTarget] 或 [WPT] 或 [wpt]
    第二行: 傳送錨點名稱（例如：家）
    ```

### 建立傳送牌 (Warp)
1. **放置告示牌** 在您想要作為傳送牌的位置
2. **編輯告示牌內容**：
    ```
    第一行: [Warp] 或 [WP] 或 [wp]
    第二行: 傳送錨點名稱（必須已存在）
    ```

### 使用傳送
1. **手持所需物品**（預設：終界珍珠）
2. **右鍵點擊** 傳送牌
3. **等待倒數** 完成後即可傳送

> ⚠️ **重要提醒**: 必須先建立傳送錨點 (WarpTarget) 才能建立對應的傳送牌 (Warp)

### 群組管理

#### 建立與管理群組
- 建立群組：`/wp group create <群組名稱>`
- 加入傳送錨點：`/wp group add <群組名稱> <傳送錨點名稱>`
- 移除傳送錨點：`/wp group remove <群組名稱> <傳送錨點名稱>`
- 邀請成員：`/wp group invite <群組名稱> <玩家名稱>`
- 移除成員：`/wp group uninvite <群組名稱> <玩家名稱>`
- 查看群組列表：`/wp group list`
- 查看群組資訊：`/wp group info <群組名稱>`
- 刪除群組：`/wp group delete <群組名稱>`
## Web 管理界面

SignWarpX 提供功能完整的 Web 管理界面，讓管理員能夠透過瀏覽器輕鬆管理所有傳送錨點。Web 界面採用現代化設計，提供直觀的操作體驗和豐富的管理功能。

### 啟用 Web 界面

在 `config.yml` 中啟用 Web 功能：

```yaml
web:
  enabled: true    # 啟用 Web 界面
  port: 8080      # Web 服務端口
```

重新載入插件後，訪問 `http://your-server-ip:8080` 即可使用 Web 管理界面。

### Web 界面特色

#### 現代化用戶體驗
- **深色/淺色主題** - 一鍵切換主題模式，保護眼睛
- **直觀操作界面** - 簡潔明瞭的設計，易於上手
- **即時數據同步** - 所有操作即時生效，無需手動刷新

#### 核心管理功能

**傳送錨點管理**
- 查看所有傳送錨點的詳細列表

**智能分類與搜尋**
- 按類型分類顯示：公共、私人、群組傳送錨點
- 進階過濾器：按創建時間、使用頻率等條件篩選

#### 詳細資訊展示

- 精確座標顯示（X, Y, Z）
- 世界名稱和維度資訊
- 維度傳送標示

**權限與訪問控制**
- 可見性狀態（公共/私人/群組）

**使用統計與分析**
- 傳送次數統計
- 最後使用時間
- 熱門傳送錨點排行

**創建者與歷史資訊**
- 創建者玩家資訊
- 創建時間戳記

### 技術特色

- **基於 Tailwind CSS** - 現代化的 CSS 框架，美觀且高效
- **JavaScript 前端** - 流暢的用戶交互體驗
- **RESTful API** - 標準化的 API 接口，支援第三方整合
- **WebSocket 即時通訊** - 實現即時數據更新和狀態同步

## 遊戲內管理介面

除了 Web 界面，SignWarpX 也提供遊戲內的 GUI 管理介面：

```bash
# 開啟管理介面
/wp gui
```

#### 功能特色
- **傳送錨點列表** - 查看所有傳送錨點
- **詳細資訊** - 檢視傳送錨點詳細資訊
- **可見性狀態** - 查看權限設定

![管理介面預覽](https://i.imgur.com/60JLVPC.gif)
##  配置設定

### API 版本管理

SignWarpX 提供智慧 API 版本控制系統，可根據伺服器版本自動選擇合適的 API：

#### 版本對應關係
- **伺服器版本 1.21.6+** → 自動使用 1.21.6 API
- **伺服器版本 1.21-1.21.5** → 自動使用 1.21 API

#### 啟動日誌示例
```
[INFO] SignWarpX: Detected server version: Paper version git-Paper-"4b72e9b" (MC: 1.21.6), using API version: API_1_21_6
```

或

```
[INFO] SignWarpX: Detected server version: Paper version git-Paper-"4b72e9b" (MC: 1.21.4), using API version: API_1_21  
```

### 基本設定

```yaml
# 使用傳送牌時需要的物品（設為 none 代表不需要）
use-item: ENDER_PEARL
# 使用傳送牌時消耗的物品數量
use-cost: 1
# 傳送前的等待時間（單位：秒）
teleport-delay: 5
# 設置傳送完成後的冷卻（單位：秒）
teleport-use-cooldown: 10
# 牽引相關設定
max-leash-depth: 5 # 最大牽引深度（防止過長的牽引鏈）

# 跨次元傳送設定
cross-dimension-teleport:
   # 是否允許跨次元傳送 (true 允許, false 禁止)
   enabled: true
   # OP 是否可以無視跨次元限制 (true 允許, false 禁止)
   op-bypass: true
```

### 傳送牌(Warp)設定

```yaml
# 傳送牌世界資訊顯示設定
sign-world-info:
   enabled: true
   format: "§7世界: §f{world-name}"
# 世界名稱顯示配置
world-display-names:
   world: "主世界"
   world_nether: "地獄"
   world_the_end: "終界"
```

### 傳送錨點(WarpTarget)設定

```yaml
# 建立 Warp Target 標誌所需的物品（設為 none 代表不需要）
create-wpt-item: DIAMOND
# 建立 Warp Target 標誌時消耗的物品數量
create-wpt-item-cost: 1
# 每位玩家最多可創建的傳送錨點數量（-1 表示無限制）
max-warps-per-player: 10
# OP 是否不受創建數量限制（true 表示 OP 無限制）
op-unlimited-warps: true
# 預設的傳送錨點可見性（true 為私人，false 為公共）
default-visibility: false
# 是否在 WPT 告示牌上顯示建立者資訊（true 表示顯示，false 表示不顯示）
show-creator-on-sign: true
```

### 群組系統設定

```yaml
warp-groups:
   # 是否啟用群組功能 (true 啟用, false 停用)
   enabled: true
   # 每位玩家最多可建立的群組數量 (OP 不受此限制)
   max-groups-per-player: 5
   # 每個群組最多可包含的傳送錨點數量 (OP 不受此限制)
   max-warps-per-group: 10
   # 每個群組最多可邀請的成員數量 (OP 不受此限制)
   max-members-per-group: 20
   # 是否允許普通玩家使用群組功能 (true 允許, false 僅限 OP)
   allow-normal-players: true
```

##  指令系統

| 指令 | 縮寫 | 功能描述 | 權限需求 |
|------|------|----------|----------|
| `/signwarp reload` | `/wp reload` | 重新載入配置檔案 | `signwarp.reload` |
| `/signwarp gui` | `/wp gui` | 開啟管理介面 | `signwarp.admin` |
| `/signwarp set <公共\|私人> <傳送錨點>` | `/wp set` | 設定傳送錨點可見性 | `signwarp.private.set` |
| `/signwarp invite <玩家> <傳送錨點>` | `/wp invite` | 邀請玩家使用私人傳送錨點 | `signwarp.invite` |
| `/signwarp uninvite <玩家> <傳送錨點>` | `/wp uninvite` | 移除玩家邀請 | `signwarp.invite` |
| `/signwarp list-invites <傳送錨點>` | `/wp list-invites` | 查看邀請列表 | `signwarp.invite.list` |
| `/signwarp tp <傳送錨點>` | `/wp tp` | 直接傳送（管理員） | `signwarp.tp` |
| `/signwarp list-own [玩家]` | `/wp list-own` | 查看自己或（OP）他人傳送錨點 | `signwarp.admin` |
| `/signwarp group ...` | `/wp group ...` | 群組相關指令（詳見下方群組說明） | 各群組權限 |

#### 群組相關指令

| 指令 | 縮寫 | 功能描述 | 權限需求 |
|------|------|----------|----------|
| `/signwarp group create <群組名稱>` | `/wp group create` | 建立新群組 | `signwarp.group.create` |
| `/signwarp group add <群組> <傳送錨點>` | `/wp group add` | 將傳送錨點加入群組 | `signwarp.group.manage` |
| `/signwarp group remove <群組> <傳送錨點>` | `/wp group remove` | 從群組中移除傳送錨點 | `signwarp.group.manage` |
| `/signwarp group invite <群組> <玩家>` | `/wp group invite` | 邀請玩家加入群組 | `signwarp.group.manage` |
| `/signwarp group uninvite <群組> <玩家>` | `/wp group uninvite` | 移除群組成員 | `signwarp.group.manage` |
| `/signwarp group list` | `/wp group list` | 列出自己擁有的群組 | `signwarp.group.create` |
| `/signwarp group info <群組名稱>` | `/wp group info` | 顯示群組詳細資訊 | 群組成員或管理員 |
| `/signwarp group delete <群組名稱>` | `/wp group delete` | 刪除群組 | `signwarp.group.manage` |

##  訊息自訂

所有插件訊息都可以在 `config.yml` 中自訂，支援 Minecraft 顏色代碼：

```yaml
messages:
  teleport-success: "&a成功傳送到 {warp-name}！"
  private_warp: "&c這是私人傳送錨點，需要邀請才能使用。"
  invite_success: "&a已邀請 {player} 使用傳送錨點 '{warp-name}'！"
  group_created: "&a成功建立群組 '{group-name}'！"
```

### 可用佔位符

- `{warp-name}` - 傳送錨點名稱
- `{player}` - 玩家名稱
- `{inviter}` - 邀請者名稱
- `{use-item}` - 所需物品名稱
- `{use-cost}` - 物品消耗數量
- `{time}` - 倒數時間
- `{cooldown}` - 冷卻時間
- `{visibility}` - 可見性狀態
- `{current}` - 玩家目前傳送錨點(WarpTarget)數量
- `{max}` - 玩家最大可建立傳送錨點(WarpTarget)數量
- `{group-name}` - 群組名稱
- `{group-owner}` - 群組擁有者名稱
- `{member-count}` - 群組成員數量
- `{warp-count}` - 群組傳送錨點數量

##  遊戲截圖

![插件使用示例](https://i.imgur.com/XRjCmyc_d.webp?maxwidth=760&fidelity=grand)

##  支援與回饋

如果您在使用過程中遇到任何問題或有功能建議，歡迎：

-  [回報問題](https://github.com/Swim5678/SignWarpX/issues)
-  [提出功能請求](https://github.com/Swim5678/SignWarpX/issues)
-  給我們一個星星來支持開發

##  授權條款

本專案使用 [MIT 授權條款](LICENSE)。

## 🙏 致謝

本專案 fork 自 [siriusbks/SignWarp](https://github.com/siriusbks/SignWarp)，感謝原作者的優秀工作。
