-- 初始化数据：使用 MERGE 保证重启幂等
MERGE INTO t_book_group (id, group_name, sort_no, remark) KEY(id) VALUES
 ('g_default', '默认分组', 0, '内置默认分组'),
 ('g_finish',  '已完结',   1, '已完结书籍归档'),
 ('g_local',   '本地导入', 2, 'TXT/EPUB 导入书籍');

MERGE INTO t_book_source (id, source_name, source_url, source_type, group_name, enabled, remark) KEY(id) VALUES
 ('src_local_server', '本地服务', 'http://127.0.0.1:8088/api/v1', 'API', '默认分组', 1, '由本服务统一提供书籍数据');

MERGE INTO t_book (id, name, author, cover_url, category, intro, status, word_count, latest_chapter_id, latest_chapter_name, chapter_count, source_name, source_url, info_url, remark) KEY(id) VALUES
 ('b_demo_001', '示例书籍', '示例作者', '', '玄幻', '这是一条初始化示例数据，可在管理页面直接修改或删除。', '连载中', '12.3万字', 'c_demo_001_0003', '第三章 风起', 3, '本地服务', 'http://127.0.0.1:8088/api/v1', '', 'demo');

MERGE INTO t_chapter (id, book_id, chapter_no, title, content, word_count, vip) KEY(id) VALUES
 ('c_demo_001_0001', 'b_demo_001', 1, '第一章 初入江湖', '示例正文：第一章内容。', 12, 0),
 ('c_demo_001_0002', 'b_demo_001', 2, '第二章 旧事', '示例正文：第二章内容。', 12, 0),
 ('c_demo_001_0003', 'b_demo_001', 3, '第三章 风起', '示例正文：第三章内容。', 12, 0);

-- App 版本默认行：仅用于首次占位，发布新版本请在管理页「版本发布」Tab 中修改。
-- 这里不能用 MERGE 覆盖，否则每次重启服务都会把管理页发布的版本重置回默认值。
INSERT INTO t_app_version (id, platform, version_code, version_name, force_update, min_version_code, download_url, update_content, enabled)
SELECT 'av_android', 'android', 1, 'v2.7.0', 0, 0, '', '服务端更新源已就绪，等待发布新版本。', 1
WHERE NOT EXISTS (SELECT 1 FROM t_app_version WHERE id = 'av_android');
