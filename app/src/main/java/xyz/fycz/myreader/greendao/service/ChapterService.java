/*
 * This file is part of FYReader.
 * FYReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FYReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FYReader.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2020 - 2022 fengyuecanzhu
 */

package xyz.fycz.myreader.greendao.service;

import android.database.Cursor;

import xyz.fycz.myreader.common.APPCONST;
import xyz.fycz.myreader.greendao.entity.BookMark;
import xyz.fycz.myreader.greendao.entity.Chapter;
import xyz.fycz.myreader.greendao.gen.ChapterDao;
import xyz.fycz.myreader.util.IOUtils;
import xyz.fycz.myreader.util.help.StringHelper;
import xyz.fycz.myreader.greendao.DbManager;
import xyz.fycz.myreader.util.utils.FileUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class ChapterService extends BaseService {
    private static volatile ChapterService sInstance;

    public static ChapterService getInstance() {
        if (sInstance == null) {
            synchronized (ChapterService.class) {
                if (sInstance == null) {
                    sInstance = new ChapterService();
                }
            }
        }
        return sInstance;
    }

    private List<Chapter> findChapters(String sql, String[] selectionArgs) {
        ArrayList<Chapter> chapters = new ArrayList<>();
        try {
            Cursor cursor = selectBySql(sql, selectionArgs);
            if (cursor == null) return chapters;
            while (cursor.moveToNext()) {
                Chapter chapter = new Chapter();
                chapter.setId(cursor.getString(0));
                chapter.setBookId(cursor.getString(1));
                chapter.setNumber(cursor.getInt(2));
                chapter.setTitle(cursor.getString(3));
                chapter.setUrl(cursor.getString(4));
                chapter.setIsVip(cursor.getInt(5) != 0);
                chapter.setIsPay(cursor.getInt(6) != 0);
                chapter.setUpdateTime(cursor.getString(7));
                chapter.setContent(cursor.getString(8));
                chapter.setStart(cursor.getInt(9));
                chapter.setEnd(cursor.getInt(10));
                chapters.add(chapter);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return chapters;
        }
        return chapters;
    }

    /**
     * 通过ID查章节
     *
     * @param id
     * @return
     */
    public Chapter getChapterById(String id) {
        ChapterDao chapterDao = DbManager.getInstance().getSession().getChapterDao();
        return chapterDao.load(id);
    }

    /**
     * 获取书的所有章节
     *
     * @return
     */
    public List<Chapter> findBookAllChapterByBookId(String bookId) {

        if (StringHelper.isEmpty(bookId)) return new ArrayList<>();


        String sql = "select * from chapter where book_id = ? order by number";

        return findChapters(sql, new String[]{bookId});
    }

    /**
     * 新增章节
     *
     * @param chapter
     */
    public void addChapter(Chapter chapter, String content) {
        chapter.setId(StringHelper.getStringRandom(25));
        addEntity(chapter);
        saveChapterCacheFile(chapter, content);
    }

    /**
     * 查找章节
     *
     * @param bookId
     * @param title
     * @return
     */
    public Chapter findChapterByBookIdAndTitle(String bookId, String title) {
        Chapter chapter = null;
        try {
            String sql = "select id from chapter where book_id = ? and title = ?";
            Cursor cursor = selectBySql(sql, new String[]{bookId, title});
            if (cursor == null) return null;
            if (cursor.moveToNext()) {
                String id = cursor.getString(0);
                chapter = getChapterById(id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return chapter;
    }

    /**
     * 删除书的所有章节
     *
     * @param bookId
     */
    public void deleteBookALLChapterById(String bookId) {
        DbManager.getInstance().getSession().getChapterDao()
                .queryBuilder()
                .where(ChapterDao.Properties.BookId.eq(bookId))
                .buildDelete()
                .executeDeleteWithoutDetachingEntities();
        deleteAllChapterCacheFile(bookId);
    }

    /**
     * 更新章节
     */
    public void updateChapter(Chapter chapter) {
        ChapterDao chapterDao = DbManager.getInstance().getSession().getChapterDao();
        chapterDao.update(chapter);
    }

    /**
     * 分段查找章节
     *
     * @param bookId
     * @param from
     * @param to
     * @return
     */
    public List<Chapter> findChapter(String bookId, int from, int to) {
        String sql = "select * from " +
                "(select row_number()over(order by number)rownumber,* from chapter where bookId = ? order by number)a " +
                "where rownumber >= ? and rownumber <= ?";

        return findChapters(sql, new String[]{bookId, String.valueOf(from), String.valueOf(to)});
    }


    /**
     * 保存或更新章节
     *
     * @param chapter
     */
    public void saveOrUpdateChapter(Chapter chapter, String content) {
        // 缓存文件名统一为「序号/ID + 标题」，避免同名章节互相覆盖
        chapter.setContent(getChapterCacheNewFile(chapter).getAbsolutePath());
        if (!StringHelper.isEmpty(chapter.getId())) {
            updateEntity(chapter);
        } else {
            addChapter(chapter, content);
        }
        saveChapterCacheFile(chapter, content);
    }

    /**
     * 批量添加章节
     */
    public void addChapters(List<Chapter> chapters) {
        ChapterDao chapterDao = DbManager.getInstance().getSession().getChapterDao();
        chapterDao.insertInTx(chapters);
    }

    /**
     * 缓存章节
     *
     * @param chapter
     */
    public void saveChapterCacheFile(Chapter chapter, String content) {
        if (StringHelper.isEmpty(content)) {
            return;
        }

        // 写入一律用新命名；旧命名的缓存文件仍可被 getChapterFile 读到，不会失效
        File file = getChapterCacheNewFile(chapter);
        // 先写同目录临时文件，写完再改名替换目标文件，
        // 避免并发请求读到被截断的半截缓存内容
        File tmpFile = new File(file.getAbsolutePath() + ".tmp");
        BufferedWriter bw = null;
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile)));
            bw.write(content);
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(bw);
        }

        if (!tmpFile.exists() || tmpFile.length() <= 0) {
            // 临时文件未写完，放弃本次落盘，避免生成残缺缓存
            deleteQuietly(tmpFile);
            return;
        }

        // renameTo 在目标已存在时可能失败，先清理旧文件
        deleteQuietly(file);
        if (!tmpFile.renameTo(file)) {
            // 改名失败（文件被占用/跨分区）时降级为流拷贝，避免缓存丢失
            copyQuietly(tmpFile, file);
            deleteQuietly(tmpFile);
        }
    }

    /**
     * 静默删除文件
     */
    private void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * 静默拷贝文件（renameTo 失败时的兜底）
     */
    private void copyQuietly(File src, File dest) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(in);
            IOUtils.close(out);
        }
    }

    /**
     * 删除章节缓存
     *
     * @param chapter
     */
    public void deleteChapterCacheFile(Chapter chapter) {
        // 新旧两种命名可能同时存在，删除时必须一并清掉，否则「重新加载」会读到残留的旧缓存
        File file = getChapterCacheNewFile(chapter);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        File legacyFile = getChapterCacheLegacyFile(chapter);
        if (!legacyFile.equals(file) && legacyFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            legacyFile.delete();
        }
    }

    /**
     * 获取缓存章节内容
     *
     * @param chapter
     * @return
     */
    public String getChapterCatheContent(Chapter chapter) {
        File file = getChapterFile(chapter);
        if (!file.exists()) return null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            StringBuilder s = new StringBuilder();
            String line = null;
            while ((line = br.readLine()) != null) {
                s.append(line);
                s.append("\n");
            }
            return s.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            IOUtils.close(br);
        }
    }

    /**
     * 更新所有章节
     *
     * @param newChapters
     */
    public void updateAllOldChapterData(List<Chapter> mChapters, List<Chapter> newChapters, String bookId) {
        int i;
        for (i = 0; i < mChapters.size() && i < newChapters.size(); i++) {
            Chapter oldChapter = mChapters.get(i);
            Chapter newChapter = newChapters.get(i);
            if (!oldChapter.getTitle().equals(newChapter.getTitle())) {
                oldChapter.setTitle(newChapter.getTitle());
                oldChapter.setUrl(newChapter.getUrl());
                oldChapter.setContent(null);
                saveOrUpdateChapter(oldChapter, null);
            }
        }
        if (mChapters.size() < newChapters.size()) {
            int start = mChapters.size();
            for (int j = mChapters.size(); j < newChapters.size(); j++) {
                newChapters.get(j).setId(StringHelper.getStringRandom(25));
                newChapters.get(j).setBookId(bookId);
                mChapters.add(newChapters.get(j));
//                mChapterService.addChapter(newChapters.get(j));
            }
            addChapters(mChapters.subList(start, mChapters.size()));
        } else if (mChapters.size() > newChapters.size()) {
            for (int j = newChapters.size(); j < mChapters.size(); j++) {
                deleteEntity(mChapters.get(j));
                deleteChapterCacheFile(mChapters.get(j));
            }
            mChapters.subList(0, newChapters.size());
        }
    }


    /**
     * 根据文件名判断是否被缓存过 (因为可能数据库显示被缓存过，但是文件中却没有的情况，所以需要根据文件判断是否被缓存
     * 过)
     *
     * @param chapter : chapter
     * @return
     */
    public static boolean isChapterCached(Chapter chapter) {
        File file = getChapterFile(chapter);
        return file.exists();
    }

    public static boolean isChapterCached(BookMark bookMark) {
        Chapter chapter = new Chapter();
        chapter.setBookId(bookMark.getBookId());
        chapter.setNumber(bookMark.getBookMarkChapterNum());
        chapter.setTitle(bookMark.getTitle());
        File file = getChapterFile(chapter);
        return file.exists();
    }

    public static int countChar(String folderName, String fileName) {//统计字符数
        int charnum = 0;//字符数
        File file = new File(APPCONST.BOOK_CACHE_PATH + folderName
                + File.separator + fileName + FileUtils.SUFFIX_FY);
        int x;
        FileReader fReader = null;
        try {
            fReader = new FileReader(file);
            while ((x = fReader.read()) != -1) {//按字符读文件，判断，符合则字符加一
                char a = (char) x;
                if (a != '\n' && a != '\r') {
                    charnum++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(fReader);
        }
        return charnum;//返回结果
    }

    private void deleteAllChapterCacheFile(String bookId) {
        FileUtils.deleteFile(APPCONST.BOOK_CACHE_PATH + bookId);
    }

    /**
     * 创建或获取存储文件
     *
     * @param folderName
     * @param fileName
     * @return
     */
    public static File getBookFile(String folderName, String fileName) {
        return FileUtils.getFile(APPCONST.BOOK_CACHE_PATH + folderName
                + File.separator + fileName + FileUtils.SUFFIX_FY);
    }

    /**
     * 章节缓存文件名（不含扩展名）
     * <p>
     * 新命名 = 「序号 + 、 + 标题」；序号异常（&lt; 0）时退化为章节ID；
     * 两者都拿不到时退化为纯标题，即旧版本命名 —— 保证任何章节都能落到唯一文件名。
     * 旧版本只用标题命名，同书内「第一章」「序章」这类同名标题会互相覆盖，
     * 这里加上序号前缀彻底消除覆盖。
     */
    public static String getChapterCacheName(Chapter chapter) {
        if (chapter == null) {
            return "";
        }
        String title = chapter.getTitle() == null ? "" : chapter.getTitle();
        String prefix = null;
        if (chapter.getNumber() >= 0) {
            prefix = String.valueOf(chapter.getNumber());
        } else if (!StringHelper.isEmpty(chapter.getId())) {
            prefix = chapter.getId();
        }
        if (StringHelper.isEmpty(prefix)) {
            return title;
        }
        return prefix + "、" + title;
    }

    /**
     * 新命名缓存文件（写入位置，不判断是否存在）
     */
    public static File getChapterCacheNewFile(Chapter chapter) {
        return new File(APPCONST.BOOK_CACHE_PATH + chapter.getBookId()
                + File.separator + getChapterCacheName(chapter) + FileUtils.SUFFIX_FY);
    }

    /**
     * 旧版本缓存文件（只含标题的命名），仅用于兼容读取
     */
    public static File getChapterCacheLegacyFile(Chapter chapter) {
        return new File(APPCONST.BOOK_CACHE_PATH + chapter.getBookId()
                + File.separator + chapter.getTitle() + FileUtils.SUFFIX_FY);
    }

    /**
     * 获取章节缓存文件：先查新命名，再回退旧命名，两个都不存在时返回新命名（写入位置）。
     * 这样升级后旧缓存仍然命中，不会因为改名让既有缓存全部失效。
     */
    public static File getChapterFile(Chapter chapter) {
        File file = getChapterCacheNewFile(chapter);
        if (file.exists() && file.length() > 0) {
            return file;
        }
        File legacyFile = getChapterCacheLegacyFile(chapter);
        if (!legacyFile.equals(file) && legacyFile.exists() && legacyFile.length() > 0) {
            return legacyFile;
        }
        return file;
    }

    /**
     * 保留旧方法名，语义与 {@link #getChapterFile(Chapter)} 一致
     */
    public static File getChapterFileExisted(Chapter chapter) {
        return getChapterFile(chapter);
    }

}
