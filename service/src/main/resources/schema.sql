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

-- App 版本表：替代原语雀 / GitLab 更新源，客户端通过 /api/v1/app/update 拉取
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
