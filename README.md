# 雀记

雀记是一个房间制的多人给分记账微信小程序。创建者开一个房间并邀请好友扫码加入，成员之间可以互相给分（转账式计分），系统实时累计每位成员的总分与排名，并记录房间流水。后续可扩展更多业务场景（不止麻将）。

## 当前功能

- 微信小程序静默登录，通过 `openid` 识别用户
- 首次进入使用弹窗选择头像并填写昵称
- 删除缓存或更换手机后，通过同一微信账号恢复用户资料和房间历史
- 创建房间，创建者自动作为第一位成员
- 邀请好友：分享小程序码或扫码加入房间
- 房间内成员间互相给分（TRANSFER），实时累计总分与排名
- 房间流水：展示“我的收入 / 我的支出”，支持按成员筛选明细
- 房间消息：按时间分组展示进出房间与给分记录（含头像、给分前分数、变动分）
- 进行中的房间自动进入，可随时退出；已结束房间展示在下方并支持“继续开始”
- 结束房间、退出房间、删除房间记录
- 用户只能访问和操作自己创建的房间
- 微信临时头像上传到后端持久化

## 技术栈

### 小程序端

- uni-app
- Vue 3
- uni-ui
- 微信小程序

### 后端

- Java 17
- Spring Boot 4
- MyBatis-Plus 3.5.17
- PostgreSQL
- Maven Wrapper

## 项目结构

```text
zc/
├─ ZcApi/                 Spring Boot 后端
│  └─ src/main/java/com/zc/zcapi/
│     ├─ controller/      接口层（Auth / Game / File）
│     ├─ service/         业务层（GameService / QrCodeService）
│     ├─ dto/             请求和响应模型（含 GameEventResponse 等）
│     ├─ entity/          数据库实体（房间 / 成员 / 给分事件）
│     ├─ mapper/          MyBatis-Plus Mapper
│     ├─ config/          Web 与认证配置
│     └─ common/          公共上下文、异常和响应
├─ ZcMiniprogram/         uni-app 小程序端
│  ├─ api/                API 和静默登录请求
│  ├─ pages/
│  │  ├─ index/           首页：房间列表（进行中 / 已结束）
│  │  └─ game/            房间页：成员、给分、房间流水、消息
│  └─ utils/              Token 与用户会话
└─ README.md
```

## 环境要求

- JDK 17
- PostgreSQL
- HBuilderX
- 微信开发者工具
- 已注册的微信小程序 AppID 和 AppSecret

## 初始化数据库

先在 PostgreSQL 中创建数据库：

```sql
CREATE DATABASE zc_mahjong;
```

后端启动时会自动执行 `ZcApi/src/main/resources/schema.sql` 创建或迁移数据表。

默认数据库配置：

```text
URL:      jdbc:postgresql://localhost:5432/zc_mahjong
Username: postgres
Password: postgres
```

推荐通过环境变量覆盖默认配置。

## 配置后端

PowerShell 示例：

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/zc_mahjong"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="你的数据库密码"
$env:WECHAT_APP_ID="你的小程序AppID"
$env:WECHAT_APP_SECRET="你的小程序AppSecret"
```

支持的环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 后端端口 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/zc_mahjong` | PostgreSQL 地址 |
| `DB_USERNAME` | `postgres` | 数据库用户名 |
| `DB_PASSWORD` | `postgres` | 数据库密码 |
| `WECHAT_APP_ID` | 无 | 微信小程序 AppID，必须配置 |
| `WECHAT_APP_SECRET` | 无 | 微信小程序 AppSecret，必须配置且不能提交到 Git |
| `TOKEN_VALID_DAYS` | `30` | 自有 Token 有效天数 |
| `UPLOAD_DIR` | `uploads` | 头像文件保存目录 |

## 启动后端

```powershell
cd ZcApi
.\mvnw.cmd spring-boot:run
```

构建验证：

```powershell
cd ZcApi
.\mvnw.cmd clean package -DskipTests
```

默认服务地址为 `http://127.0.0.1:8080`。

## 配置并运行小程序

1. 使用 HBuilderX 打开 `ZcMiniprogram`。
2. 在 `ZcMiniprogram/manifest.json` 的 `mp-weixin.appid` 中填写小程序 AppID。
3. 确保该 AppID 与后端的 `WECHAT_APP_ID` 相同。
4. 在 HBuilderX 中运行到微信开发者工具。

开发环境 API 地址位于：

```text
ZcMiniprogram/api/game.js
```

模拟器默认使用：

```text
http://127.0.0.1:8080
```

真机调试时，`127.0.0.1` 指向手机本身，需要改成电脑的局域网 IP，例如：

```text
http://192.168.1.10:8080
```

正式发布时必须使用已备案的 HTTPS 域名，并在微信公众平台配置 request 和 uploadFile 合法域名。

## 静默登录流程

```text
小程序 uni.login 获取临时 code
→ POST /api/auth/wechat-login
→ 后端调用微信 code2Session
→ 获取当前用户 openid
→ 查找或创建 app_user
→ 返回自有 Token 和用户资料
→ 后续请求携带 Authorization: Bearer <token>
```

`openid` 在“同一微信账号 + 同一小程序 AppID”范围内保持不变，因此清除本地缓存或更换手机后，可以重新静默登录并恢复服务端数据。

微信不会静默返回用户头像和昵称。首次使用时仍需要用户主动点击选择头像并填写昵称，保存后资料存入后端数据库。

## 主要接口

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/wechat-login` | 微信静默登录 |
| `GET` | `/api/users/me` | 查询当前用户资料 |
| `PUT` | `/api/users/me` | 更新当前用户头像昵称 |
| `POST` | `/api/files/avatars` | 上传头像 |
| `GET` | `/api/games` | 查询当前用户所在房间列表 |
| `POST` | `/api/games` | 创建房间 |
| `GET` | `/api/games/{id}` | 查询房间详情（成员 / 流水 / 事件 / 总分） |
| `POST` | `/api/games/scan-join` | 扫码加入房间 |
| `GET` | `/api/games/{id}/invite` | 获取房间邀请小程序码 |
| `POST` | `/api/games/{id}/transfers` | 房间内成员间给分 |
| `DELETE` | `/api/games/{id}` | 删除房间记录 |
| `POST` | `/api/games/{id}/leave` | 退出房间 |
| `POST` | `/api/games/{id}/restart` | 已结束房间继续开始 |
| `POST` | `/api/games/{id}/finish` | 结束房间 |

除微信登录接口外，其他 `/api/**` 请求都需要携带后端签发的 Token。

## 数据与安全说明

- `AppSecret` 只能配置在后端，禁止写入小程序代码或提交到 Git。
- 数据库只保存自有 Token 的 SHA-256 哈希，不保存 Token 明文。
- 当前用户只能访问自己创建的房间。
- 房间成员通过微信账号关联；通过邀请/扫码加入的成员会绑定其微信 `openid`，头像昵称来自其个人资料。
- `uploads/` 保存本地上传头像，已加入 `.gitignore`；部署时应使用持久磁盘或对象存储。
