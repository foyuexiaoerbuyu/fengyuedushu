# fyreader-service

风月读书（fangyuedushu / FYReader）服务端。**用自建后端替代 App 内置的书源爬虫解析链路**：App 侧不再需要 `third3` 规则引擎 + `ThirdSourceApi` 抓取网页或解析源站，改为直接调用本服务的 REST 接口取「书籍列表 / 书籍详情 / 目录 / 章节正文」。

- 技术栈：Spring Boot 2.7.18 + MyBatis 2.3.1 + H2 2.1.214（文件模式，`MODE=MySQL`）+ Lombok
- JDK：1.8（本机 `JAVA_HOME=D:\01App\01dev\zip_jdk\jdk1.8.0_202`）
- 端口：8088
- 管理页面：<http://127.0.0.1:8088/>（原生 HTML + JS，无前端构建）
- 数据文件：`./data/fyreader.mv.db`（首次启动自动执行 `schema.sql` / `data.sql`）

## 目录结构

```
service/
├── pom.xml
└── src/main/
    ├── java/com/fyreader/service/
    │   ├── ServiceApplication.java          启动类，@MapperScan
    │   ├── common/                          统一响应与异常
    │   │   ├── Result.java                  统一响应体 {code,message,data,timestamp}
    │   │   ├── ResultCode.java              错误码枚举
    │   │   ├── PageResult.java              分页结构 {records,total,page,size,pages}
    │   │   ├── BusinessException.java       业务异常（携带 code）
    │   │   ├── GlobalExceptionHandler.java  全局异常收敛为 Result
    │   │   └── ApiResponseAdvice.java       Controller 返回值自动包装（免手写 Result.ok）
    │   ├── config/
    │   │   ├── FyReaderProperties.java      fyreader.* 配置绑定
    │   │   ├── TokenInterceptor.java        /api/v1/** 可选 token 鉴权
    │   │   └── WebConfig.java               拦截器、CORS、根路径跳转管理页
    │   ├── controller/
    │   │   ├── OpenApiController.java       /api/v1/**  供 App 调用
    │   │   └── AdminApiController.java      /api/admin/** 供管理页面调用
    │   ├── entity/                          Book / Chapter / BookSource / BookGroup
    │   ├── dto/                             BookQuery / ImportResult
    │   ├── mapper/                          MyBatis Mapper 接口
    │   └── service/                         BookService / ChapterService / BookSourceService / ImportService
    └── resources/
        ├── application.yml
        ├── schema.sql                       建表脚本
        ├── data.sql                         初始化数据
        ├── mapper/*.xml                     SQL 映射
        └── static/index.html                管理页面
```

## 快速启动

```powershell
cd D:\02Code\09github_me\fengyuedushu\service
mvn -B clean package -DskipTests
java -jar target\fyreader-service.jar
# 浏览器打开 http://127.0.0.1:8088/
```

开发期也可 `mvn spring-boot:run`。

## 配置项（application.yml）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | 8088 | 服务端口 |
| `spring.datasource.url` | `jdbc:h2:file:./data/fyreader;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE` | 文件库；注意 H2 2.1.214 不支持 `AUTO_SERVER=TRUE` 与 `DB_CLOSE_ON_EXIT=FALSE` 同时出现 |
| `spring.h2.console.enabled` | true | 控制台 `/h2-console`，JDBC URL 同上，用户 `sa`、密码空 |
| `spring.sql.init.mode` | always | 每次启动执行 schema/data（脚本均为幂等写法） |
| `fyreader.auth-enabled` | false | 是否校验 `X-FY-Token` |
| `fyreader.token` | `fyreader-lite-token` | 上述开关启用时要求的值，也支持 `?token=` |
| `fyreader.default-page-size` | 20 | 分页默认大小 |
| `fyreader.max-page-size` | 200 | 分页上限，超出截断 |

## 数据表

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `t_book` | 书籍本体 | id, name, author, category, status, word_count, latest_chapter_id, latest_chapter_name, chapter_count, source_name, source_url, info_url |
| `t_chapter` | 章节 | id, book_id, chapter_no, title, content(CLOB), word_count, vip |
| `t_book_source` | 书源元数据（**已剥离爬虫规则**） | id, source_name, source_url, source_type, group_name, enabled |
| `t_book_group` | 分组 | id, group_name, sort_no |

对应 App 侧 GreenDao 实体时，客户端本地字段（阅读进度、缓存标记、Cookie、搜索规则等）一律不进服务端。

## 与 App 侧旧链路的对应关系

| App 侧（待下线） | 服务端替代 |
| --- | --- |
| `model/third3/webBook/BookList.kt`（搜索结果） | `GET /api/v1/books?keyword=` |
| `model/third3/webBook/BookInfo.kt`（书籍详情） | `GET /api/v1/books/{id}` |
| `model/third3/webBook/BookChapterList.kt`（目录） | `GET /api/v1/books/{id}/catalog` |
| `model/third3/webBook/BookContent.kt`（正文） | `GET /api/v1/books/{id}/chapters/{chapterId}` |
| `model/third3/analyzeRule`（规则解析 9 文件 3,126 行） | 全部不需要，规则留在服务端入库阶段处理 |
| `model/third3/http`（OkHttp 抓取 6 文件 806 行） | App 侧仅保留基础网络层访问本服务 |
| `model/sourceAnalyzer`（书源管理 1,292 行） | `GET /api/v1/sources`，规则字段不再下发 |
| `entity/rule/BookSource` 中 searchRule/infoRule/tocRule/contentRule/loginUrl | 服务端 `t_book_source` 不建这些列 |

## 接口速查

客户端（`/api/v1`，详见 `docs/API.md`）：

```
GET /api/v1/health
GET /api/v1/books?keyword=&category=&status=&sourceName=&sort=&page=&size=
GET /api/v1/books/{id}
GET /api/v1/books/{id}/catalog
GET /api/v1/books/{id}/chapters?page=&size=&withContent=
GET /api/v1/books/{id}/chapters/{chapterId}
GET /api/v1/books/{id}/chapters/no/{chapterNo}
GET /api/v1/books/{id}/chapters/{chapterNo}/neighbor?direction=next|prev
GET /api/v1/sources
GET /api/v1/groups
```

管理端（`/api/admin`，供 `index.html` 使用）：

```
GET    /api/admin/stats
GET    /api/admin/books        POST /api/admin/books
GET    /api/admin/books/{id}   PUT  /api/admin/books/{id}      DELETE /api/admin/books/{id}
POST   /api/admin/books/{id}/refresh                      重算章节数与最新章节
GET    /api/admin/books/{id}/chapters
POST   /api/admin/books/{id}/chapters
PUT    /api/admin/chapters/{chapterId}   DELETE /api/admin/chapters/{chapterId}
GET    /api/admin/sources      POST /api/admin/sources
PUT    /api/admin/sources/{id} DELETE /api/admin/sources/{id}
POST   /api/admin/import/txt   multipart: file,bookName,author,category,encoding
```

统一响应（成功）：

```json
{ "code": 0, "message": "ok", "data": {}, "timestamp": 1794887671558, "success": true }
```

分页 `data`：

```json
{ "records": [], "total": 1, "page": 1, "size": 20, "pages": 1 }
```

失败时 `code` 取 400 / 401 / 404 / 409 / 500，`message` 为可直接展示的中文原因。

## TXT 导入规则

- 章节标题支持：`第X章/节/回/卷/集/篇`、`Chapter N`、`CHAPTER N`（整行匹配）
- 标题前的内容归入「前言」章节；未指定书名且首行为不超过 40 字的非章节标题时，取该行作书名并从正文剔除
- 编码支持 UTF-8（去 BOM）、GBK、GB18030
- 导入后自动重排 `chapter_no`、统计字数、回写书籍的章节数与最新章节

## 后续可做

1. App 侧改造：新增 Retrofit/OkHttp 封装指向 `/api/v1`，移除 `third3.analyzeRule`、`third3.http` 的书源抓取分支，`SourceAnalyzer` 退化为只读接口列表
2. 服务端补采集器（可选）：把书源抓取搬到服务端定时任务，App 彻底不接触规则与源站
3. 换库：改 `spring.datasource` 为 MySQL，`schema.sql` 已是 MySQL 兼容语法，需替换 `MERGE INTO` 为 `INSERT ... ON DUPLICATE KEY UPDATE`
4. 分页/排序：`BookQuery.sort` 走 `BookService.resolveOrderBy` 白名单，新增排序需在该方法注册
