-- 初始化数据：全部使用 INSERT ... SELECT ... WHERE NOT EXISTS 保证重启幂等
-- 注意：不能用 MERGE 覆盖，否则每次重启都会把管理页里修改/删除过的数据重置回默认值。

-- 内置分组
INSERT INTO t_book_group (id, group_name, sort_no, remark)
SELECT 'g_default', '默认分组', 0, '内置默认分组'
WHERE NOT EXISTS (SELECT 1 FROM t_book_group WHERE id = 'g_default');
INSERT INTO t_book_group (id, group_name, sort_no, remark)
SELECT 'g_finish', '已完结', 1, '已完结书籍归档'
WHERE NOT EXISTS (SELECT 1 FROM t_book_group WHERE id = 'g_finish');
INSERT INTO t_book_group (id, group_name, sort_no, remark)
SELECT 'g_local', '本地导入', 2, 'TXT/EPUB 导入书籍'
WHERE NOT EXISTS (SELECT 1 FROM t_book_group WHERE id = 'g_local');

-- 内置书源
INSERT INTO t_book_source (id, source_name, source_url, source_type, group_name, enabled, remark)
SELECT 'src_local_server', '本地服务', 'http://127.0.0.1:8088/api/v1', 'API', '默认分组', 1, '由本服务统一提供书籍数据'
WHERE NOT EXISTS (SELECT 1 FROM t_book_source WHERE id = 'src_local_server');

-- 示例书籍
INSERT INTO t_book (id, name, author, cover_url, category, intro, status, word_count,
                    latest_chapter_id, latest_chapter_name, chapter_count, source_name, source_url, info_url, remark)
SELECT 'b_demo_001', '示例书籍', '示例作者', '', '玄幻', '这是一条初始化示例数据，可在管理页面直接修改或删除。',
       '连载中', '12.3万字', 'c_demo_001_0003', '第三章 风起', 3, '本地服务', 'http://127.0.0.1:8088/api/v1', '', 'demo'
WHERE NOT EXISTS (SELECT 1 FROM t_book WHERE id = 'b_demo_001');

-- 示例章节
INSERT INTO t_chapter (id, book_id, chapter_no, title, content, word_count, vip)
SELECT 'c_demo_001_0001', 'b_demo_001', 1, '第一章 初入江湖', '示例正文：第一章内容。', 12, 0
WHERE NOT EXISTS (SELECT 1 FROM t_chapter WHERE id = 'c_demo_001_0001');
INSERT INTO t_chapter (id, book_id, chapter_no, title, content, word_count, vip)
SELECT 'c_demo_001_0002', 'b_demo_001', 2, '第二章 旧事', '示例正文：第二章内容。', 12, 0
WHERE NOT EXISTS (SELECT 1 FROM t_chapter WHERE id = 'c_demo_001_0002');
INSERT INTO t_chapter (id, book_id, chapter_no, title, content, word_count, vip)
SELECT 'c_demo_001_0003', 'b_demo_001', 3, '第三章 风起', '示例正文：第三章内容。', 12, 0
WHERE NOT EXISTS (SELECT 1 FROM t_chapter WHERE id = 'c_demo_001_0003');

-- App 版本默认行：仅用于首次占位，发布新版本请在管理页「版本发布」Tab 中修改。
INSERT INTO t_app_version (id, platform, version_code, version_name, force_update, min_version_code, download_url, update_content, enabled)
SELECT 'av_android', 'android', 1, 'v2.7.0', 0, 0, '', '服务端更新源已就绪，等待发布新版本。', 1
WHERE NOT EXISTS (SELECT 1 FROM t_app_version WHERE id = 'av_android');
