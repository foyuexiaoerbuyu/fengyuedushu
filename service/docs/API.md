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

`fyreader.auth-enabled=true` 时，`/api/v1/**` 需携带请求头 `X-FY-Token: <token>`（或查询参数 `?token=`）。
`/api/admin/**` 与静态页面不受此开关影响，默认内网自用。

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
| file | 是 | TXT 文件，上限 64MB |
| bookName | 否 | 留空时先取首行短标题，再退回文件名 |
| author | 否 | 默认「佚名」 |
| category | 否 | 默认「其他」 |
| encoding | 否 | 默认 UTF-8，支持 GBK / GB18030 |

响应：

```json
{
  "code": 0, "message": "ok",
  "data": { "bookId": "b_4cfe...", "bookName": "导入测试书", "author": "导入作者",
            "chapterCount": 3, "wordCount": 43, "encoding": "UTF-8",
            "chapterPreview": ["第一章 开端", "第二章 发展", "第三章 结束"],
            "message": "导入成功" },
  "timestamp": 1794887697783, "success": true
}
```

解析不到章节时返回 `code=400`，`message="未解析到任何章节，请检查文件内容或更换编码"`。

---

## 3. 管理页面

打开 <http://127.0.0.1:8088/> 即为管理页面（根路径自动跳转 `/index.html`），含四个模块：

- 书籍管理：关键词搜索、排序、分页、新增/编辑/删除、重算统计
- 章节管理：选书后分页查看目录、编辑正文、新增/删除章节
- 书源管理：元数据增删改
- TXT 导入：上传文件并显示导入结果

页面为纯原生 HTML + `fetch`，无构建步骤，直接放在 `src/main/resources/static/` 下随 jar 发布。
