# 11501 AIOT — Android Socket 聊天室

一個專案、兩個 module（兩個 APK）：

| Module | App 名稱 | 第一頁 | 第二頁 |
|---|---|---|---|
| `server` | Socket Server | 輸入 Server 名稱 | 開 ServerSocket(6100)、顯示 Server IP、誰進來/離開、聊天訊息；Server 也能發言 |
| `client` | Socket Client | 輸入名稱、IP、Port | 聊天、在線名單、LEAVE 正常離開（其他人會看到「xxx has left.」） |

## 功能

- 多 Client 同時連線（每個 Client 一條執行緒）
- 加入時廣播 `Welcome xxx join us!`，離開時廣播 `xxx has left.`
- 上方紫色列即時顯示在線名單
- Client 按 LEAVE / 返回鍵：先送 `leave` 再關 Socket，不會報錯
- Server 按 LEAVE：通知所有 Client `Server closed. Please press 'LEAVE' button.`，Client 不會閃退，只會停用 SEND
- 訊息格式：一行一個 JSON（`println` / `readLine`），UTF-8，中文沒問題

## 程式位置

- `server/src/main/java/com/example/chatserver/ChatActivity.java` — accept 迴圈、handleClient、broadcast
- `client/src/main/java/com/example/chatclient/ChatActivity.java` — 連線、接收執行緒、leave
- `*/Protocol.java` — 訊息格式說明

---

# Demo 詳細步驟

整體架構（跟講義第 76 頁一樣）：

```
Client_A (emulator-5556) ─┐
                          ├─ 10.0.2.2:6100 ─► 電腦 localhost:6100 ─(adb forward)─► Server (emulator-5554):6100
Client_B (emulator-5558) ─┘
```

## Step 0：讓模擬器開成「獨立視窗」（錄影一定要做）

Android Studio 預設把模擬器塞在右側的 **Running Devices** 分頁裡，一次只看得到一台，錄影時沒辦法三台並排。

1. 上方選單 **File → Settings**
2. 左側 **Tools → Emulator**
3. **取消勾選**「Launch in the Running Devices tool window」
4. 按 **OK**

之後模擬器會各自跳出一個視窗，可以並排擺放。

## Step 1：開啟專案

1. Android Studio 上方選單 **File → Open...**
2. 選 `D:\Project\Andoird\11501_AIOT_TA_chat` 資料夾（選資料夾本身，不用點進去）→ **OK**
3. 跳出「Trust Project?」→ **Trust Project**
4. 等右下角狀態列的 **Gradle Sync** 跑完（第一次可能要 1～2 分鐘）
5. 左側 **Project** 面板（左邊直排的資料夾圖示，或 `Alt+1`），上方下拉選 **Android** 檢視，會看到 `server` 和 `client` 兩個 module

> Sync 失敗說 JDK 有問題：**File → Settings → Build, Execution, Deployment → Build Tools → Gradle**，**Gradle JDK** 選 Android Studio 內建的 JetBrains Runtime（`jbr`，版本 25）。

## Step 2：建立 3 台模擬器

1. 開 **Device Manager**：上方選單 **Tools → Device Manager**（或右側直排工具列的手機圖示）
2. 你已經有一台 `Medium Phone`，再做兩台：
   - 點 `Medium Phone` 那一列右邊的 **⋮ → Duplicate**，重複兩次
   - 或點 **+ → Create Virtual Device** → 選 **Medium Phone** → **Next** → 選已下載的系統映像（API 37）→ **Next** → **Finish**
3. 建議改名方便辨認（**⋮ → Edit** 改 AVD Name）：`Server_Phone`、`Client_A`、`Client_B`

> 三台同時開很吃記憶體。電腦卡的話，在 **Edit → Show Advanced Settings** 把 RAM 調成 2048 MB。

## Step 3：依序啟動 3 台模擬器

在 Device Manager **依序**按每台右邊的 **▶**：先 `Server_Phone`，等它開好再開 `Client_A`，最後 `Client_B`。

依序開的話編號會是：

| AVD | 序號（serial） |
|---|---|
| Server_Phone | emulator-5554 |
| Client_A | emulator-5556 |
| Client_B | emulator-5558 |

確認方式見 Step 5 的 `adb devices`。

## Step 4：把 App 裝到模擬器

Android Studio **最上方工具列**中間有兩個下拉選單和一個綠色 ▶：

```
[ server ▾ ]  [ Server_Phone ▾ ]  ▶ (Run)
  ↑ 要跑哪個 module   ↑ 要裝到哪台
```

1. 左邊下拉選 **server**，右邊下拉選 **Server_Phone** → 按綠色 **▶**（或 `Shift+F10`）
2. 左邊下拉改成 **client**，右邊選 **Client_A** → **▶**
3. 右邊改選 **Client_B** → **▶**

> 也可以在右邊的裝置下拉選 **Select Multiple Devices...**，勾 Client_A、Client_B，一次裝兩台。
> 如果左邊下拉沒有 server / client：**File → Sync Project with Gradle Files** 再看一次。

## Step 5：設定 adb forward（關鍵步驟）

1. 打開 Android Studio 的 **Terminal**：**View → Tool Windows → Terminal**（或 `Alt+F12`，或左下角的 `>_` 圖示）
2. 先看三台模擬器的序號：

   ```powershell
   C:\Android\SDK\platform-tools\adb.exe devices
   ```

   應該看到：

   ```
   emulator-5554   device
   emulator-5556   device
   emulator-5558   device
   ```

3. 把電腦的 6100 轉到 **Server 那台**（5554）的 6100：

   ```powershell
   C:\Android\SDK\platform-tools\adb.exe -s emulator-5554 forward tcp:6100 tcp:6100
   ```

   成功會印出 `6100`。

> 如果你已照講義把 `platform-tools` 加進環境變數，可以直接打 `adb devices`。
> **模擬器重開後 forward 會消失**，要重打一次。
> 如果 Server 不是 5554（看 `adb devices` 和模擬器視窗標題），把指令裡的序號換掉。

## Step 6：錄影前擺好視窗

1. 三個模擬器視窗並排：Server 在左，Client_A 中間，Client_B 右邊（同講義參考畫面）
2. 開始錄影：
   - **Win + Alt + R**（Xbox Game Bar，會錄目前視窗；要錄全螢幕建議用下面的方法）
   - **Win + Shift + R**（剪取工具的錄影，可框選範圍，Win 11 適用）
   - 或用 OBS

## Step 7：Demo 劇本（照講義第 79～81 頁）

| # | 在哪台 | 操作 | 應該看到 |
|---|---|---|---|
| 1 | Server | 開 **Socket Server** App → Name 輸入名稱 → **START SERVER** | `Server started (IP:6100)`、`xxx joined the chat room.` |
| 2 | Client_A | 開 **Socket Client** → Name `Client1`、IP `10.0.2.2`、Port `6100` → **CONNECT** | 三台都看到 `Welcome Client1 join us!`，Server 顯示 `Client1 (127.0.0.1:xxxxx) connected.` |
| 3 | Client_B | 同上，Name 改 `Client2` → **CONNECT** | 三台都看到 `Welcome Client2 join us!`，上方在線名單變 3 人 |
| 4 | Server | 輸入 `Hi~Message from Server` → **SEND** | 三台都看到 |
| 5 | Client_A | 輸入 `Hi~Message from Client1` → **SEND** | 三台都看到 |
| 6 | Client_B | 輸入 `Hi~Message from Client2` → **SEND** | 三台都看到 |
| 7 | Client_A | 按 **LEAVE** | Client_A 回到第一頁、不報錯；Server、Client_B 看到 `Client1 has left.`，在線名單少一人 |
| 8 | Server | 按 **LEAVE** | Server 回到第一頁；Client_B 看到 `Server closed. Please press 'LEAVE' button.` 和 `Disconnected from server.`，SEND 變灰 |
| 9 | Client_B | 按 **LEAVE** | 回到第一頁，不報錯 |

> 模擬器打字：直接用電腦鍵盤就可以，點一下輸入框後打字。

## 常見問題

| 狀況 | 原因 / 解法 |
|---|---|
| Client 跳出「連線失敗：... timed out / refused」 | Server 還沒按 START，或沒做 Step 5 的 forward（模擬器重開後要重做） |
| Server 顯示 `Server failed to start: Address already in use` | 上一次的 Server 還沒完全關。在 Server 模擬器把 App 關掉（多工鍵往上滑）再開 |
| 改了程式之後畫面沒變 | 要重新按 ▶ 裝到對應那台模擬器 |
| 三台跑很卡 | Device Manager → Edit 降 RAM；或關掉其他程式 |

## 上傳 GitHub

1. 上方選單 **Git → GitHub → Share Project on GitHub**
   （舊版是 **VCS → Share Project on GitHub**；第一次會要你登入 GitHub）
2. Repository name：`11501_AIOT_TA_chat_學號`
3. 依需要勾 **Private**（記得助教要看得到，或之後再邀請/公開），按 **Share**
4. 跳出要加入哪些檔案 → 全選 → **Add**

`.gitignore` 已經排除 `build/`、`local.properties` 等不用上傳的檔案。
