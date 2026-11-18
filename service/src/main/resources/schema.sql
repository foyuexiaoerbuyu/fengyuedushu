-- ============================================================
-- 风月读书 lite 版 后端服务 H2 表结构
-- 兼容 MySQL 语法模式（MODE=MySQL）
-- ============================================================

-- 书籍主表：只存书籍本体信息，客户端本地阅读状态（进度/分组/排序）不上传
CREATE TABLE IF NOT EXISTS t_book (
    id                   VARCHAR(64)  PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    author               VARCHAR(128),
    cover_url            VARCHAR(1024),
    category             VARCHAR(64),
    intro                CLOB,
    status               VARCHAR(32),
    word_count           VARCHAR(32),
    latest_chapter_id    VARCHAR(64),
    latest_chapter_name  VARCHAR(512),
    chapter_count        INT          DEFAULT 0,
    source_name          VARCHAR(128),
    source_url           VARCHAR(1024),
    info_url             VARCHAR(1024),
    remark               VARCHAR(1024),
    create_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_book_name   ON t_book(name);
CREATE INDEX IF NOT EXISTS idx_book_author ON t_book(author);
CREATE INDEX IF NOT EXISTS idx_book_update ON t_book(update_time);

-- 章节表：content 为章节正文，为空表示正文延迟加载
CREATE TABLE IF NOT EXISTS t_chapter (
    id          VARCHAR(64) PRIMARY KEY,
    book_id     VARCHAR(64) NOT NULL,
    chapter_no  INT          DEFAULT 0,
    title       VARCHAR(512),
    content     CLOB,
    word_count  INT          DEFAULT 0,
    vip         TINYINT      DEFAULT 0,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chapter_book ON t_chapter(book_id, chapter_no);

-- 书源表：仅保留书源元数据（名称/地址/类型），不再存爬虫解析规则
CREATE TABLE IF NOT EXISTS t_book_source (
    id          VARCHAR(64)  PRIMARY KEY,
    source_name VARCHAR(128) NOT NULL,
    source_url  VARCHAR(1024),
    source_type VARCHAR(32)  DEFAULT 'API',
    group_name  VARCHAR(64),
    enabled     TINYINT      DEFAULT 1,
    remark      VARCHAR(1024),
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_source_name ON t_book_source(source_name);

-- 书籍分组（客户端书架分组，服务端仅提供默认分组元数据）
CREATE TABLE IF NOT EXISTS t_book_group (
    id         VARCHAR(64) PRIMARY KEY,
    group_name VARCHAR(64) NOT NULL,
    sort_no    INT         DEFAULT 0,
    remark     VARCHAR(512),
    create_time TIMESTAMP  DEFAULT CURRENT_TIMESTAMP
);

-- App 版本表：App 更新检查的唯一数据来源，客户端通过 /api/v1/app/update 拉取
CREATE TABLE IF NOT EXISTS t_app_version (
    id                VARCHAR(64) PRIMARY KEY,
    platform          VARCHAR(32)  DEFAULT 'android',
    version_code      INT          NOT NULL,
    version_name      VARCHAR(64),
    force_update      TINYINT      DEFAULT 0,
    min_version_code  INT          DEFAULT 0,
    download_url      VARCHAR(1024),
    update_content    CLOB,
    domain            VARCHAR(255),
    plugin_config_url VARCHAR(1024),
    splash_image_url  VARCHAR(1024),
    splash_image_md5  VARCHAR(64),
    enabled           TINYINT      DEFAULT 1,
    create_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_version_platform ON t_app_version(platform, version_code);

-- ============================================================
-- A+B 鉴权改造新增：用户表 + 登录会话表
-- 说明：密码只存 BCrypt 摘要（$2a$ 格式，60 字符，留 100 余量）。
--       username 唯一；status=1 启用、0 停用。
-- ============================================================
CREATE TABLE IF NOT EXISTS t_user (
    id            VARCHAR(64)  PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64),
    status        TINYINT      DEFAULT 1,
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_username ON t_user(username);

-- 会话表：短期 token（高熵随机串），落库可吊销；过期由业务层校验并顺带清理
CREATE TABLE IF NOT EXISTS t_user_session (
    token       VARCHAR(128) PRIMARY KEY,
    user_id     VARCHAR(64)  NOT NULL,
    expire_time TIMESTAMP    NOT NULL,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_user ON t_user_session(user_id);

-- ============================================================
-- 账号角色（幂等 DDL）：ADMIN 管理端账号 / USER 普通用户
-- 存量账号一律按 USER 处理；管理员账号由启动引导程序创建，不写死在 sql 中
-- ============================================================
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS role VARCHAR(16) DEFAULT 'USER';

UPDATE t_user SET role = 'USER' WHERE role IS NULL;

-- ============================================================
-- 书籍归属与可见性（幂等 DDL，可重复执行）
-- owner_id 为空表示"系统/管理端书籍"；visibility 为 PUBLIC / PRIVATE
-- 存量书籍默认 PUBLIC，保证改造上线后匿名用户仍可访问原有全部书籍
-- ============================================================
ALTER TABLE t_book ADD COLUMN IF NOT EXISTS owner_id VARCHAR(64);
ALTER TABLE t_book ADD COLUMN IF NOT EXISTS visibility VARCHAR(16) DEFAULT 'PUBLIC';

UPDATE t_book SET visibility = 'PUBLIC' WHERE visibility IS NULL;

CREATE INDEX IF NOT EXISTS idx_book_owner ON t_book(owner_id);
