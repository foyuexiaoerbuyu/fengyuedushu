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
