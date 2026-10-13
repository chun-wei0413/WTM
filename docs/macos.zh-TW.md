# 在 Mac 上執行 WTM,並把圖庫搬過去

這份文件寫給「新的 MacBook 有程式碼,但沒有圖庫」的情況:梗圖、描述、帳號和搜尋索引都在一直在跑網站的那台電腦上。
照下面的步驟,可以把全部資料搬過去。同樣的兩個腳本也是網站的備份與還原。

## 什麼會搬、什麼不會

| 會搬(在匯出資料夾裡) | 不會搬 |
|---|---|
| 資料庫:梗圖、描述與標籤、含向量的搜尋索引、帳號、收藏、回報、搜尋紀錄 | `.env`:裡面是機密,每台電腦各自產生 |
| 每一張圖片(原樣的檔案) | Ollama 模型(在新電腦重新下載,見步驟 2) |
| `MANIFEST.txt`:筆數與檢查碼 | `node_modules`、建置輸出、`target/` |

**為什麼不直接複製資料夾。** 不同種類的處理器,資料庫檔案彼此讀不了:Intel 或 AMD 上的 PostgreSQL 資料夾,在 Apple 晶片
上打不開。圖片儲存也有自己的檔案排列方式。資料庫備份檔加上原樣的圖片,在每台電腦都能用,所以匯出的是這兩樣。

## 1. 舊電腦:匯出

兩個容器要開著(`docker compose up -d`),應用程式不用關。

```bash
bash scripts/export-data.sh
```

它會寫到 `<資料資料夾>/backups/wtm-export-<日期>/`(500 張梗圖約 100 MB),並檢查每一張梗圖都有圖片。
如果印出警告,請不要使用這份匯出。**搬家前再跑一次**,最後加的收藏與梗圖才會一起帶走。

把整個 `wtm-export-<日期>` 資料夾複製到 Mac:AirDrop、外接硬碟或雲端硬碟都可以,要整個資料夾一起複製。

## 2. Mac:安裝工具

需要 Docker、Java 21、Maven、Node(20 或更新)、Git。用 [Homebrew](https://brew.sh):

```bash
brew install --cask docker-desktop temurin@21      # Docker Desktop、Java 21
brew install maven node git ollama                 # Node 要 20 或更新
```

Maven 會自己帶一個比較新的 Java,所以要指定它用 21(把這行加進 `~/.zshrc` 才會一直生效):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

先打開一次 Docker Desktop,等它顯示引擎已經在執行。接著下載模型(合計約 7 GB;看圖模型建議有 16 GB 記憶體):

```bash
ollama serve &                              # 或直接打開 Ollama 應用程式
ollama pull bge-m3                          # 依意思搜尋
ollama pull qwen2.5vl:7b                    # 看圖
```

三個容器映像(`pgvector/pgvector:pg16`、`rustfs/rustfs:1.0.0`、`amazon/aws-cli`)都有 Apple 晶片的版本。

## 3. Mac:取得程式碼,建立全新的環境

```bash
git clone https://github.com/chun-wei0413/WTM.git && cd WTM
bash scripts/init-env.sh                    # 產生含新隨機機密的 .env;資料放在 ~/wtm-data
docker compose up -d
```

`init-env.sh` 不會覆蓋已存在的 `.env`,也不會印出機密。想把資料放在別顆硬碟,在後面加資料夾:
`bash scripts/init-env.sh /Volumes/Big/wtm-data`。

## 4. Mac:匯入圖庫

在這台 Mac 還沒有啟動過應用程式之前:

```bash
bash scripts/import-data.sh ~/Downloads/wtm-export-<日期>
```

它會用 `MANIFEST.txt` 檢查備份檔、載入資料庫與圖片,最後把筆數和匯出時比對。如果資料庫裡已經有梗圖,
除非加上 `--force`,否則不會動它。

## 5. 選模型並啟動

在 `.env` 加幾行(三個可填的值是 `mock | ollama | gemini`,見 `.env.example`):

```
WTM_VISION_PROVIDER=ollama
WTM_EMBEDDING_PROVIDER=ollama
WTM_EXPLAINER_PROVIDER=ollama
```

用 `gemini` 的話,還要在這台 Mac 的 `.env` 自己加 `GEMINI_API_KEY=...`;金鑰不會跟著匯出。然後:

```bash
mvn spring-boot:run                         # 應用程式,http://localhost:8080
cd web && npm install && npm run dev        # 網頁,http://localhost:5173
```

**登入。** 帳號是跟著資料庫一起來的,所以用**舊電腦上管理員的密碼**登入。新 `.env` 裡的 `WTM_ADMIN_PASSWORD`
只有在「還沒有任何管理員」時才會用到。

## 或者:整個網站都放在 Docker 裡跑

步驟 4 和 5 是從原始碼執行,適合開發。如果只是要讓網站一直開著,用 `docker-compose.app.yml` 把全部放進容器
(只有一個網址 http://localhost:8080;映像檔建好之後不需要 Java 或 Node)。

**在 Mac 上自己建映像檔。** Docker Hub 上的映像檔只有 Intel/AMD 版本,而且可能比你的程式碼舊
(資料庫不能比應用程式新)。用手上的程式碼建:

```bash
docker build -t wtm-local/wtm-backend:local .
docker build -t wtm-local/wtm-web:local web
```

**匯入到這組網站自己的容器。** 這個檔案把資料庫和圖片放在它自己的 Docker volume,不在 `.env` 的資料夾裡,
容器名稱也不一樣。只先啟動這兩個,並告訴匯入腳本它們的名字:

```bash
docker compose -f docker-compose.app.yml up -d postgres object-storage
PG_CONTAINER=wtm-app-postgres-1 S3_CONTAINER=wtm-app-object-storage-1 \
  bash scripts/import-data.sh ~/Downloads/wtm-export-<日期>
```

**啟動**,`.env` 裡要有步驟 5 的模型設定(Ollama 跑在 Mac 上,不在容器裡;用 `brew services start ollama`
讓它常駐):

```bash
IMAGE_NAMESPACE=wtm-local WTM_VERSION=local docker compose -f docker-compose.app.yml up -d
```

重開機後執行同一行即可。改過程式碼的話,要先重建映像檔。要停掉:
`docker compose -f docker-compose.app.yml down`(volume 和圖庫都會保留;千萬不要加 `-v`)。
匯出腳本也要加同樣的兩個名字:`PG_CONTAINER=wtm-app-postgres-1 S3_CONTAINER=wtm-app-object-storage-1 bash scripts/export-data.sh`。

## 出問題時

| 看到什麼 | 代表什麼 |
|---|---|
| `bad interpreter: /bin/bash^M` | 腳本是用 Windows 的換行存的。用 clone 的不會發生(`.gitattributes` 擋住了);如果是手動複製檔案,執行 `sed -i '' 's/\r$//' scripts/*.sh` |
| `The database '...' did not become ready` | Docker Desktop 還在啟動,等一分鐘再跑一次 |
| `This database already holds N memes` | 這台 Mac 已經有圖庫。真的要取代才加 `--force` |
| `db.dump does not match MANIFEST.txt` | 複製過程損壞了,重新複製整個資料夾 |
| 最後的筆數對不上 | 不要使用這次匯入。回舊電腦重新匯出,對照 `MANIFEST.txt` |
| 梗圖都在,但搜尋找不到 | 搜尋索引要用嵌入模型:執行 `ollama list` 確認有 `bge-m3` |
| 登入一直說密碼錯誤 | 管理員帳號是跟著資料庫搬過來的,所以密碼是**舊電腦** `.env` 裡的 `WTM_ADMIN_PASSWORD`,不是 Mac 的。忘記的話看下面 |

**忘記管理員密碼時**,把它改成這台 Mac `.env` 裡的 `WTM_ADMIN_PASSWORD`。在專案資料夾、資料庫有在跑的情況下執行
(整個網站放在 Docker 裡跑的話,把 `wtm-postgres` 換成 `wtm-app-postgres-1`):

```bash
PW="$(grep '^WTM_ADMIN_PASSWORD=' .env | cut -d= -f2-)"
docker exec -i wtm-postgres psql -q -U wtm -d wtm -v ON_ERROR_STOP=1 -v pw="$PW" <<'SQL'
create extension if not exists pgcrypto;
update app_user set password_hash = crypt(:'pw', gen_salt('bf', 10)) where role = 'ADMIN';
drop extension pgcrypto;
SQL
```

只會改密碼,帳號、收藏和梗圖都不動。失敗太多次會暫時鎖住那個位址的登入,稍等一下再試。

## 當作備份用

同樣的腳本就是備份與還原:偶爾執行 `scripts/export-data.sh`,把資料夾放到別的地方保管。
要還原時,照步驟 3 和 4 從全新的環境開始。
