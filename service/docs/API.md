---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 58e87cd5ac29bab7322874bd6b207f1c_ea337339e28a11f1b140525400de85a5
    ReservedCode1: 3VEBkABeL+LyTO3GBeRw1Kcn35ThQe+e/of7J1HUZDMPWYAfzf1F+KaMAKWvhdDhIaYtpniUZRAGe+FQx6Ob+SIlUQlp5m5NP3YMGJd1mTihSWIyj/qBDL4+IYS7xQV2a3+wE/xYUH0ETx1sF5PKr/kmEpQuTm8Y7C3ZKt8oQubgjscJLVIc0x3BpdA=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 58e87cd5ac29bab7322874bd6b207f1c_ea337339e28a11f1b140525400de85a5
    ReservedCode2: 3VEBkABeL+LyTO3GBeRw1Kcn35ThQe+e/of7J1HUZDMPWYAfzf1F+KaMAKWvhdDhIaYtpniUZRAGe+FQx6Ob+SIlUQlp5m5NP3YMGJd1mTihSWIyj/qBDL4+IYS7xQV2a3+wE/xYUH0ETx1sF5PKr/kmEpQuTm8Y7C3ZKt8oQubgjscJLVIc0x3BpdA=
---



# fyreader-service 接口文档

Base URL：`http://127.0.0.1:8088`

## 0. 通用约定

### 0.1 统一响应体

所有接口（含异常）都返回同一结构，由 `ApiResponseAdvice` + `GlobalExceptionHandler` 保证：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "timestamp": 1794887671558,
  "success": true
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | int | 0 成功；400 参数错误；401 未授权；404 不存在；409 冲突；500 服务端异常 |
| message | string | 可直接展示的中文提示 |
| data | any | 业务数据，可能为对象、数组、分页对象或 null |
| timestamp | long | 服务端毫秒时间戳 |
| success | boolean | `code == 0` |

错误示例：

```json
{ "code": 404, "message": "书籍不存在: b_xxx", "timestamp": 1794887671000, "success": false }
```

### 0.2 分页对象

```json
{ "records": [], "total": 1, "page": 1, "size": 20, "pages": 1 }
```

`page` 从 1 开始；`size` 超过 `fyreader.max-page-size`（默认 200）时按上限截断。

### 0.3 鉴权

鉴权由 `TokenInterceptor` 统一完成，按路径前缀分流，两套凭据互不通用：

| 路径 | 凭据 | 说明 |
| --- | --- | --- |
| `/api/v1/**` | 请求头 `X-FY-Token: <FYREADER_TOKEN>`，或查询参数 `?token=` | 受 `fyreader.auth-enabled` 控制（默认 `true`）；`FYREADER_TOKEN` 未配置时一律 401 |
| `/api/admin/**` | 请求头 `X-Admin-Token: <FYREADER_ADMIN_TOKEN>`，或查询参数 `?adminToken=` | 始终校验，不受 `auth-enabled` 影响；未配置管理口令时一律 401 |

例外与说明：

- `GET /api/v1/app/apk` 不鉴权——App 用系统浏览器下载 APK，浏览器不会带自定义请求头。
- 静态资源（`/`、`/index.html`、`/chapter.html`）不鉴权，但页面加载后会先要求输入管理口令，校验通过才拉取数据、渲染管理界面。
- `?adminToken=` 供**新窗口页面**使用：`window.open` 打开的新标签页无法携带自定义请求头，章节详情页 `/chapter.html` 因此通过查询参数口令调用管理接口；口令错误统一返回 401，页面随即要求重新输入并回填到地址栏。
- 校验失败返回 `401` + `{"code":401,"message":"token 无效或缺失"}`（管理端为「管理口令无效或缺失」）。凭据比较走定长比较，避免时序侧信道。

### 0.4 时间与字段

- 时间格式统一 `yyyy-MM-dd HH:mm:ss`，时区 GMT+8
- 响应中的 `null` 字段被省略
- JSON 字段使用小驼峰，数据库列为下划线（`map-underscore-to-camel-case=true`）

---

## 1. 客户端接口 `/api/v1`

### 1.1 健康检查 `GET /api/v1/health`

```json
{
  "code": 0, "message": "ok",
  "data": { "status": "UP", "service": "fyreader-service", "time": 1794887671558 },
  "timestamp": 1794887671558, "success": true
}
```

### 1.2 书籍列表 / 搜索 / 发现 `GET /api/v1/books`

列表、关键词搜索、分类筛选共用此接口。

| 参数 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| keyword | 否 | - | 书名或作者模糊匹配 |
| category | 否 | - | 分类精确匹配 |
| status | 否 | - | 连载中 / 已完结 |
| sourceName | 否 | - | 来源名称精确匹配 |
| sort | 否 | update_desc | `update_desc` / `update_asc` / `name_asc` / `create_desc`（白名单，其余回退默认） |
| page | 否 | 1 | 页码 |
| size | 否 | 20 | 每页条数，上限 200 |

响应 `data` 为分页对象，`records` 元素：

```json
{
  "id": "b_demo_001",
  "name": "示例书籍",
  "author": "示例作者",
  "category": "玄幻",
  "intro": "这是一条初始化示例数据，可在管理页面直接修改或删除。",
  "status": "连载中",
  "wordCount": "12.3万字",
  "latestChapterId": "c_demo_001_0003",
  "latestChapterName": "第三章 风起",
  "chapterCount": 3,
  "sourceName": "本地服务",
  "sourceUrl": "http://127.0.0.1:8088/api/v1",
  "createTime": "2026-11-17 11:44:16",
  "updateTime": "2026-11-17 11:44:16"
}
```

> 列表不含章节正文，仅含最新章节与章节总数，适合书架、搜索、发现页。

### 1.3 书籍详情 `GET /api/v1/books/{id}`

响应 `data` 为单个书籍对象（字段同上）。

### 1.4 全量目录 `GET /api/v1/books/{id}/catalog`

返回 `data` 为章节数组，**不含正文**，按 `chapter_no` 升序，适合阅读页目录一次性渲染：

```json
[
  { "id": "c_demo_001_0001", "bookId": "b_demo_001", "chapterNo": 1, "title": "第一章 初入江湖",
    "wordCount": 12, "vip": 0, "createTime": "2026-11-17 11:44:16", "updateTime": "2026-11-17 11:44:16" }
]
```

### 1.5 目录分页 `GET /api/v1/books/{id}/chapters`

| 参数 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| page / size | 否 | 1 / 20 | 分页 |
| withContent | 否 | false | true 时同时返回 `content`（长书慎用） |

响应 `data` 为分页对象，元素同 1.4（`withContent=true` 时多 `content` 字段）。

### 1.6 章节正文 `GET /api/v1/books/{id}/chapters/{chapterId}`

按章节主键取正文，会校验章节归属，跨书访问返回 404：

```json
{
  "code": 0, "message": "ok",
  "data": { "id": "c_demo_001_0001", "bookId": "b_demo_001", "chapterNo": 1,
            "title": "第一章 初入江湖", "content": "示例正文：第一章内容。",
            "wordCount": 12, "vip": 0,
            "createTime": "2026-11-17 11:44:16", "updateTime": "2026-11-17 11:44:16" },
  "timestamp": 1794887671811, "success": true
}
```

### 1.7 按序号取正文 `GET /api/v1/books/{id}/chapters/no/{chapterNo}`

`chapterNo` 从 1 开始，响应同 1.6。客户端保存阅读进度时只需存序号，不必存章节主键。

### 1.8 上一章 / 下一章 `GET /api/v1/books/{id}/chapters/{chapterNo}/neighbor`

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| direction | 否 | `next`（默认）/ `prev` |

返回相邻章节正文（含 `content`）；到首章 / 末章时 `data` 为 `null`。

### 1.9 书源列表 `GET /api/v1/sources`

仅返回启用中的书源元数据，**不含任何解析规则**：

```json
[
  { "id": "src_local_server", "sourceName": "本地服务", "sourceUrl": "http://127.0.0.1:8088/api/v1",
    "sourceType": "API", "groupName": "默认分组", "enabled": 1,
    "remark": "由本服务统一提供书籍数据",
    "createTime": "2026-11-17 11:44:16", "updateTime": "2026-11-17 11:44:16" }
]
```

### 1.10 分组列表 `GET /api/v1/groups`

```json
[
  { "id": "g_default", "groupName": "默认分组", "sortNo": 0, "remark": "内置默认分组" },
  { "id": "g_finish",  "groupName": "已完结",   "sortNo": 1, "remark": "已完结书籍归档" },
  { "id": "g_local",   "groupName": "本地导入", "sortNo": 2, "remark": "TXT/EPUB 导入书籍" }
]
```

### 1.11 App 更新检查 `GET /api/v1/app/update`

App「关于 → 检查更新」的唯一数据源，由自建 fyreader-service 服务端接口 `/api/v1/app/update` 提供。

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| platform | 否 | 平台标识，默认 `android` |
| versionCode | 否 | 客户端当前 `versionCode`，默认 `0`；用于计算 `hasUpdate` 与强制更新 |

```json
{
  "code": 0, "message": "ok",
  "data": {
    "hasUpdate": true,
    "versionCode": 271,
    "versionName": "v2.7.1",
    "forceUpdate": false,
    "minVersionCode": 260,
    "downloadUrl": "/api/v1/app/apk",
    "updateContent": "1. 修复若干问题\n2. 优化阅读体验",
    "domain": "",
    "pluginConfigUrl": "",
    "splashImageUrl": "",
    "splashImageMd5": ""
  },
  "timestamp": 1794887697783, "success": true
}
```

判定规则：

- `hasUpdate = 服务端最新 versionCode > 客户端 versionCode`
- `forceUpdate = (force_update == 1) || (客户端 versionCode < min_version_code 且 min_version_code > 0)`
- 版本记录中 `download_url` 为空时，接口自动返回 `/api/v1/app/apk`（客户端按 baseUrl 拼接为绝对地址）
- 只下发站内路径：`download_url` 必须以单个 `/` 开头才会原样返回；配置成外部地址（`http://`、`https://`、`//host`）、含反斜杠或 `..` 时，一律回落到 `/api/v1/app/apk`
- 无版本记录时返回 `hasUpdate=false`、`versionCode=0`，各字符串字段为空

### 1.12 下载最新 APK `GET /api/v1/app/apk`

从 `fyreader.apk-dir`（默认 `./data/apk`）目录中取**修改时间最新**的 `.apk` 文件流式返回，`Content-Type: application/octet-stream`，带 `Content-Disposition: attachment`。该接口直接返回 `ResponseEntity`，不套统一响应体。目录不存在或无 apk 文件时返回 `404`。

---

## 2. 管理端接口 `/api/admin`

供静态管理页面 `classpath:/static/index.html` 调用，全部为 JSON 读写，返回体同样被统一包装。

### 2.1 概览 `GET /api/admin/stats`

```json
{ "bookCount": 1, "chapterCount": 3, "sourceCount": 1 }
```

### 2.2 书籍

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/books` | 参数同 1.2，返回分页书籍 |
| GET | `/api/admin/books/{id}` | 详情，不存在返回 404 |
| POST | `/api/admin/books` | 新增，`name` 必填；`id` 可省略，服务端生成 `b_` + UUID |
| PUT | `/api/admin/books/{id}` | 局部更新，仅更新传入字段 |
| DELETE | `/api/admin/books/{id}` | 删除书籍并级联删除其全部章节 |
| POST | `/api/admin/books/{id}/refresh` | 按章节表重算 `chapterCount` 与 `latestChapterId/latestChapterName` |

新增请求示例：

```json
{ "name": "接口自测书", "author": "测试作者", "category": "测试", "status": "连载中" }
```

### 2.3 章节

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/books/{id}/chapters` | 分页目录，`withContent=true` 带正文 |
| GET | `/api/admin/books/{id}/chapters/{chapterId}/detail` | 取单章正文与统计，供管理页新窗口打开章节详情 |
| POST | `/api/admin/books/{id}/chapters` | 新增章节；`chapterNo` 省略时自动取 `MAX(chapter_no)+1` |
| PUT | `/api/admin/chapters/{chapterId}` | 更新标题 / 正文 / VIP；字数自动重算 |
| DELETE | `/api/admin/chapters/{chapterId}` | 删除章节并回写书籍统计 |

新增请求示例：

```json
{ "title": "第一章 自测", "content": "这是自测正文内容，用于验证统一封装。", "vip": 0 }
```

返回：

```json
{ "code": 0, "message": "ok",
  "data": { "id": "c_xxx", "bookId": "b_xxx", "chapterNo": 1, "title": "第一章 自测",
            "content": "这是自测正文内容，用于验证统一封装。", "wordCount": 18, "vip": 0 },
  "timestamp": 1794887680000, "success": true }
```

章节详情 `GET /api/admin/books/{id}/chapters/{chapterId}/detail` 返回：

```json
{ "code": 0, "message": "ok",
  "data": { "bookId": "b_xxx", "bookName": "狼群", "author": "刺血",
            "chapterId": "c_xxx", "chapterNo": 1, "title": "第一章 岩浆山洞",
            "wordCount": 4435, "vip": 0, "updateTime": "2026-11-17 21:34:23",
            "content": "章节正文……" },
  "timestamp": 1794924126000, "success": true }
```

书籍或章节不存在时返回 `code=400`（`书籍不存在: xxx` / `章节不存在: xxx`）。该接口同样支持 `?adminToken=` 查询参数鉴权，供 `/chapter.html` 新窗口页面直接调用。

### 2.4 书源

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/sources` | 全部书源（含未启用） |
| POST | `/api/admin/sources` | 新增，`sourceName` 必填；`id` 省略时生成 `src_` + UUID，默认 `sourceType=API`、`enabled=1` |
| PUT | `/api/admin/sources/{id}` | 更新 |
| DELETE | `/api/admin/sources/{id}` | 删除 |

### 2.5 TXT 导入 `POST /api/admin/import/txt`

`multipart/form-data`：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| file | 是 | TXT 文件，业务上限 64MB（需整体读入内存解析，故与 APK 的 200MB 上限分开校验） |
| bookName | 否 | 留空时先解析文件头（`书名：xxx` / `《xxx》`），再取首个标题前的短标题，最后退回文件名 |
| author | 否 | 留空时先解析文件头 `作者：xxx`，再默认「佚名」 |
| category | 否 | 默认「其他」 |
| encoding | 否 | 留空＝自动识别（BOM → 严格 UTF-8 试探 → 回落 GB18030）；显式传值（UTF-8 / GBK / GB18030 等）时按传入值解码 |

响应：

```json
{
  "code": 0, "message": "ok",
  "data": { "bookId": "b_4cfe...", "bookName": "导入测试书", "author": "导入作者",
            "chapterCount": 3, "wordCount": 43,
            "encoding": "GB18030", "encodingDetectedBy": "UTF-8 不成立，回落 GB18030",
            "reusedBook": false, "droppedEmptyChapters": 0, "droppedDuplicateChapters": 0,
            "chapterPreview": ["前言", "第一章 开端", "尾声"],
            "message": "导入成功" },
  "timestamp": 1794887697783, "success": true
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| encoding | 实际采用的编码 |
| encodingDetectedBy | 编码判定依据：`BOM(EF BB BF)` / `严格 UTF-8 探测通过` / `UTF-8 不成立，回落 GB18030` |
| reusedBook | 是否命中同名书籍并复用其 `bookId`（章节会按本次解析结果重建） |
| droppedEmptyChapters | 因正文为空被丢弃的章节数 |
| droppedDuplicateChapters | 因标题重复被忽略的章节数 |

章节标题识别范围：`第X章/节/回/卷/集/部/篇/幕`、`前言/序章/序言/序幕/自序/楔子/引子/正文/尾声/终章/大结局/结局/附录`、`番外`（可带编号）、`Chapter N`；标题行独立成章，行首缩进与空行容错，含句号的行按正文处理。

解析不到章节时返回 `code=400`，`message="未解析到任何章节，请检查文件内容或更换编码"`。

### 2.6 版本配置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/app/version?platform=android` | 读取该平台最新版本记录，供「版本发布」Tab 回填；无记录时 `data=null` |
| PUT | `/api/admin/app/version` | 保存版本记录：`id` 命中则更新，否则新建（`av_` + UUID） |

请求体字段（`versionCode` 必填）：

```json
{
  "id": "av_xxx",
  "platform": "android",
  "versionCode": 271,
  "versionName": "v2.7.1",
  "forceUpdate": 0,
  "minVersionCode": 260,
  "downloadUrl": "",
  "updateContent": "1. 修复若干问题\n2. 优化阅读体验",
  "domain": "",
  "splashImageUrl": "",
  "splashImageMd5": "",
  "pluginConfigUrl": "",
  "enabled": 1
}
```

省略字段时默认 `platform=android`、`forceUpdate=0`、`minVersionCode=0`、`enabled=1`。保存成功后返回落库后的完整记录。

`downloadUrl` 只接受空值或以单个 `/` 开头的站内路径，否则返回 `400`：外部地址（如 `https://example.com/app.apk`）提示「下载地址只允许站内路径（以 / 开头）或留空」，协议相对地址（`//host/x.apk`）与含 `..`、反斜杠的路径也会被拒。发版时推荐留空或填 `/api/v1/app/apk`，即由服务端分发最新上传的安装包。

### 2.7 APK 安装包

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/admin/app/apk` | `multipart/form-data` 上传安装包，字段名 `file` |
| GET | `/api/admin/app/apk/list` | 列出 `fyreader.apk-dir` 目录内的安装包，按修改时间倒序 |

上传约束（实现见 `ApkStorageService`）：

| 约束 | 说明 |
| --- | --- |
| 扩展名 | 仅 `.apk`（大小写不敏感），其余返回 `400`「只允许上传 .apk 安装包」 |
| 大小 | 单个文件上限 200MB，超出返回 `400`「上传文件超过大小上限，安装包最大 200MB」 |
| 文件名 | 剥离路径分隔符与 `\ / : * ? " < > \|`、控制字符，去掉隐藏前缀，主体截断至 96 字符后补 `.apk` |
| 重名 | 不覆盖：同名文件改写为 `名称_yyyyMMdd_HHmmss.apk`，仍冲突则再加 `_2`、`_3` 序号 |
| 落盘 | `fyreader.apk-dir`（默认 `./data/apk`），与 `GET /api/v1/app/apk` 读取目录一致 |

上传响应：

```json
{ "code": 0, "message": "ok",
  "data": { "fileName": "small.apk", "size": 4400, "sizeText": "4.3 KB",
            "md5": "cbe793c09f2ab586694f699245d0a1a8",
            "lastModified": 1794924125905, "downloadUrl": "/api/v1/app/apk" },
  "timestamp": 1794924126000, "success": true }
```

列表响应 `data`：

```json
{ "total": 2, "downloadUrl": "/api/v1/app/apk",
  "records": [ { "fileName": "small_20261117_220205.apk", "size": 4400, "sizeText": "4.3 KB",
                 "lastModified": 1794924125905, "downloadUrl": "/api/v1/app/apk", "current": true },
               { "fileName": "small.apk", "size": 4400, "sizeText": "4.3 KB",
                 "lastModified": 1794924125905, "downloadUrl": "/api/v1/app/apk", "current": false } ] }
```

`current=true` 标记 `/api/v1/app/apk` 实际下发的包（按修改时间取最新），仅一条为真。

---

## 3. 管理页面

打开 <http://127.0.0.1:8088/> 即为管理页面（根路径自动跳转 `/index.html`）。页面先显示管理口令输入框，口令校验通过（内部探测 `GET /api/admin/stats`）后才渲染管理界面并加载数据；所有请求自动携带 `X-Admin-Token`，口令失效或返回 401 时会退回登录页。含五个模块：

- 书籍管理：关键词搜索、排序、分页、新增/编辑/删除、重算统计
- 章节管理：选书后分页查看目录、编辑正文、新增/删除章节；点击章节标题或「查看」在新窗口打开 `/chapter.html?bookId=&chapterId=&adminToken=` 阅读正文（自带排版，口令失效时在页内重新输入）
- 书源管理：元数据增删改
- 版本发布：维护 App 更新检查返回的版本号 / 下载地址 / 更新说明，对应 `/api/v1/app/update`；同页可上传 APK 安装包（带进度条，展示文件名/大小/MD5），并列出目录内已有安装包，一键把 `downloadUrl` 填成 `/api/v1/app/apk`
- TXT 导入：上传文件并显示导入结果

页面为纯原生 HTML + `fetch`，无构建步骤，直接放在 `src/main/resources/static/` 下随 jar 发布。
*（内容由AI生成，仅供参考）*
*（内容由AI生成，仅供参考）*
