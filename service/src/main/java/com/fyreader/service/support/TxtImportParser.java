package com.fyreader.service.support;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT 小说解析器：负责「编码识别」与「章节切分」两件事，供 {@code ImportService} 调用。
 *
 * <p>编码识别顺序（避免 GBK/GB18030 文件被当成 UTF-8 读出乱码，也避免二次转码）：
 * <ol>
 *   <li>BOM 优先：EF BB BF → UTF-8，FF FE → UTF-16LE，FE FF → UTF-16BE</li>
 *   <li>严格 UTF-8 试探解码（{@link CodingErrorAction#REPORT}，遇到非法字节立即失败）</li>
 *   <li>UTF-8 失败后回落 GB18030（GBK 超集，GBK 文件必定可正确解码）</li>
 *   <li>用户显式指定且严格的编码，在上述步骤无法给出一致结果时生效</li>
 * </ol>
 * 解码结果在内存中一律为 Java String（UTF-16），落库由 JDBC/H2 统一按 UTF-8 处理，不存在二次转码。
 *
 * <p>章节切分覆盖：第X章/节/回/卷/集/部/篇/幕、前言、序章/序言/序幕、楔子、引子、正文、
 * 尾声、后记、终章、结局、番外、附录，以及 Chapter N / CHAPTER N。
 * 标题行必须独立成为章节标题（含行首全角/半角缩进容错），不会被并入上一章正文；
 * 内容为空的章节与重复标题会被过滤。
 */
public final class TxtImportParser {

    /** 解码结果 */
    public static final class Decoded {
        public final String text;
        /** 实际使用的编码名 */
        public final String charsetName;
        /** 识别依据，便于后台核对 */
        public final String detectedBy;

        Decoded(String text, String charsetName, String detectedBy) {
            this.text = text;
            this.charsetName = charsetName;
            this.detectedBy = detectedBy;
        }
    }

    /** 解析结果 */
    public static final class Parsed {
        public final String bookName;
        public final String author;
        public final List<ChapterDraft> chapters;
        /** 被过滤的空章节数 */
        public final int droppedEmpty;
        /** 被忽略的重复标题行数 */
        public final int droppedDuplicate;

        Parsed(String bookName, String author, List<ChapterDraft> chapters,
               int droppedEmpty, int droppedDuplicate) {
            this.bookName = bookName;
            this.author = author;
            this.chapters = chapters;
            this.droppedEmpty = droppedEmpty;
            this.droppedDuplicate = droppedDuplicate;
        }
    }

    /** 章节草稿（与持久化实体解耦） */
    public static final class ChapterDraft {
        public final String title;
        public final String content;

        ChapterDraft(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    // ------------------------------------------------------------------ 章节标题规则

    /** 第X章 / 第12回 / 第一百零八节 ... （中文数字、阿拉伯数字、全角数字均支持） */
    private static final Pattern NUMBERED_TITLE = Pattern.compile(
            "^第\\s*[0-9０-９零〇一二三四五六七八九十百千万亿两壹贰叁肆伍陆柒捌玖拾佰仟]{1,12}"
                    + "\\s*[章节回卷集部篇幕][^\\n]{0,30}$");

    /** 无编号固定标题：前言 / 序章 / 楔子 / 正文 / 尾声 / 后记 ... 关键词后面只能跟可选的短副标题 */
    private static final Pattern SPECIAL_TITLE = Pattern.compile(
            "^(?:序章|序言|序幕|自序|前言|楔子|引子|正文|尾声|尾聲|后记|後記|终章|終章|"
                    + "大结局|结局|結局|附录|附錄)(?:[ \t]+[^ \t].{0,20})?$",
            Pattern.CASE_INSENSITIVE);

    /** 番外可能带编号或副标题，如「番外一」「番外篇 缘起」 */
    private static final Pattern EXTRA_TITLE = Pattern.compile(
            "^番外[篇章]?(?:[ \t]*[0-9０-９零一二三四五六七八九十百两]{0,6})(?:[ \t]+[^ \t].{0,20})?$");

    /** Chapter 12 / CHAPTER XII ... */
    private static final Pattern ENGLISH_TITLE = Pattern.compile(
            "^(?:Chapter|CHAPTER|chapter)[ \t]*[0-9IVXLC]{1,8}(?:[ \t]+[^ \t].{0,30})?$");

    /** 标题行最大长度，超过则认为是被截断的正文而非标题 */
    private static final int MAX_TITLE_LENGTH = 80;

    // ------------------------------------------------------------------ 文件头元信息规则

    private static final Pattern META_BOOK_NAME = Pattern.compile("^书\\s*名\\s*[:：]\\s*(.{1,60})$");
    private static final Pattern META_AUTHOR = Pattern.compile("^作\\s*者\\s*[:：]\\s*(.{1,40})$");
    private static final Pattern META_QUOTED_NAME = Pattern.compile("^《\\s*([^》]{1,40})\\s*》$");
    private static final Pattern SEPARATOR_LINE = Pattern.compile("^[-=*_~—·．.\\s]{3,}$");

    /** 文件头中常见的下载站/声明噪音关键词（仅作用于首个章节标题之前的区域） */
    private static final List<String> NOISE_KEYWORDS = Arrays.asList(
            "声明", "本作品来自互联网", "请支持正版", "版权归", "用户上传", "存储空间",
            "电子书", "免费下载", "TXT全集", "www.", "http", "https", "更多免费", "仅供",
            "本站", "txt8080", "八零");

    private TxtImportParser() {
    }

    // ================================================================== 编码识别

    /**
     * 按 BOM → 严格 UTF-8 → GB18030 的顺序识别编码并解码。
     *
     * @param raw               原始字节
     * @param preferredEncoding 调用方显式指定的编码，可为空
     */
    public static Decoded decode(byte[] raw, String preferredEncoding) {
        if (raw == null || raw.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }

        // 1. BOM 优先
        if (raw.length >= 3 && u(raw[0]) == 0xEF && u(raw[1]) == 0xBB && u(raw[2]) == 0xBF) {
            return new Decoded(new String(raw, 3, raw.length - 3, Charset.forName("UTF-8")),
                    "UTF-8", "BOM(EF BB BF)");
        }
        if (raw.length >= 2 && u(raw[0]) == 0xFF && u(raw[1]) == 0xFE) {
            return new Decoded(new String(raw, 2, raw.length - 2, Charset.forName("UTF-16LE")),
                    "UTF-16LE", "BOM(FF FE)");
        }
        if (raw.length >= 2 && u(raw[0]) == 0xFE && u(raw[1]) == 0xFF) {
            return new Decoded(new String(raw, 2, raw.length - 2, Charset.forName("UTF-16BE")),
                    "UTF-16BE", "BOM(FE FF)");
        }

        // 2. 严格 UTF-8 试探：UTF-8 字节结构约束极强，通过即基本可确定为 UTF-8
        String utf8 = strictDecode(raw, "UTF-8");
        if (utf8 != null && looksSane(utf8)) {
            return new Decoded(utf8, "UTF-8", "严格 UTF-8 探测通过");
        }

        // 3. 用户显式指定且可严格解码时优先采纳（排除默认占位的 UTF-8）
        if (preferredEncoding != null && preferredEncoding.trim().length() > 0) {
            String preferred = normalizeCharsetName(preferredEncoding.trim());
            if (preferred != null && !"UTF-8".equalsIgnoreCase(preferred)) {
                String text = strictDecode(raw, preferred);
                if (text != null && looksSane(text)) {
                    return new Decoded(text, preferred, "用户指定编码严格解码通过");
                }
            }
        }

        // 4. 回落 GB18030（GBK 超集）
        String gb = strictDecode(raw, "GB18030");
        if (gb != null && looksSane(gb)) {
            return new Decoded(gb, "GB18030", "UTF-8 不成立，回落 GB18030");
        }
        // GB18030 理论上覆盖全部字节组合，走到这里说明文件异常，做最后兜底
        String fallback = new String(raw, Charset.forName("GB18030"));
        return new Decoded(fallback, "GB18030", "兜底解码（文件可能含异常字节）");
    }

    private static int u(byte b) {
        return b & 0xFF;
    }

    private static String normalizeCharsetName(String name) {
        try {
            return Charset.forName(name).name();
        } catch (Exception e) {
            return null;
        }
    }

    /** 严格解码：遇到非法/不可映射字节立即返回 null，绝不静默替换成 U+FFFD */
    private static String strictDecode(byte[] raw, String charsetName) {
        try {
            CharsetDecoder decoder = Charset.forName(charsetName).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer buffer = decoder.decode(ByteBuffer.wrap(raw));
            return buffer.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 粗筛解码结果是否"像正常文本"：连续替换符或典型 mojibake 标记视为异常 */
    private static boolean looksSane(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int sample = Math.min(text.length(), 8000);
        int bad = 0;
        for (int i = 0; i < sample; i++) {
            char c = text.charAt(i);
            if (c == '\uFFFD' || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')) {
                bad++;
            }
        }
        if (bad > sample / 100) {
            return false;
        }
        if (text.contains("锟斤拷") || text.contains("ï¿½") || text.contains("\uFFFD")) {
            return false;
        }
        return true;
    }

    // ================================================================== 章节切分

    /** 该行（已 normalize）是否为章节标题行 */
    public static boolean isTitleLine(String normalizedLine) {
        if (normalizedLine == null) {
            return false;
        }
        String line = normalizedLine.trim();
        if (line.isEmpty() || line.length() > MAX_TITLE_LENGTH) {
            return false;
        }
        // 含句号的行几乎必是正文句子（标题不使用句号），避免把正文误判成章节标题
        if (line.indexOf('\u3002') >= 0) {
            return false;
        }
        if (NUMBERED_TITLE.matcher(line).matches()) {
            return true;
        }
        if (EXTRA_TITLE.matcher(line).matches()) {
            return true;
        }
        if (ENGLISH_TITLE.matcher(line).matches()) {
            return true;
        }
        // 固定标题允许带极短副标题，如「尾声 归途」
        if (SPECIAL_TITLE.matcher(line).matches() && line.length() <= 20) {
            return true;
        }
        return false;
    }

    /**
     * 解析整篇文本。
     *
     * @param text          已知编码解码后的全文
     * @param bookNameInput 调用方指定的书名，可为空
     */
    public static Parsed parse(String text, String bookNameInput) {
        List<String> rawLines = splitLines(text);
        List<String> lines = new ArrayList<>(rawLines.size());
        for (String raw : rawLines) {
            lines.add(normalize(raw));
        }

        // 首个章节标题位置，之前的内容视作文件头
        int firstTitle = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (isTitleLine(lines.get(i))) {
                firstTitle = i;
                break;
            }
        }

        String bookName = emptyToNull(bookNameInput);
        String author = null;
        List<String> headerBody = new ArrayList<>();
        for (int i = 0; i < firstTitle; i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            Matcher m = META_BOOK_NAME.matcher(line);
            if (m.matches()) {
                if (bookName == null) {
                    bookName = m.group(1).trim();
                }
                continue;
            }
            m = META_AUTHOR.matcher(line);
            if (m.matches()) {
                if (author == null) {
                    author = m.group(1).trim();
                }
                continue;
            }
            m = META_QUOTED_NAME.matcher(line);
            if (m.matches()) {
                if (bookName == null) {
                    bookName = m.group(1).trim();
                }
                continue;
            }
            if (SEPARATOR_LINE.matcher(line).matches() || isNoise(line)) {
                continue;
            }
            headerBody.add(line);
        }
        // 书名单行兜底：文件头首行是短标题且未识别到书名
        if (bookName == null && !headerBody.isEmpty() && headerBody.get(0).length() <= 40) {
            bookName = headerBody.remove(0);
        }

        List<ChapterDraft> chapters = new ArrayList<>();
        List<ChapterDraft> declared = new ArrayList<>();
        // 全局去重键：标题 + 正文摘要，用于识别文件内重复粘贴的整章
        Set<String> seenKeys = new HashSet<>();
        String currentTitle = null;
        StringBuilder body = new StringBuilder();
        ChapterStat stat = new ChapterStat();

        for (int i = firstTitle; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isTitleLine(line)) {
                String title = cleanTitle(line);
                if (title.equals(currentTitle)) {
                    // 连续出现的同一标题行：视为重复标题，忽略本行，内容仍归上一章
                    stat.duplicate++;
                    continue;
                }
                collect(currentTitle, body, chapters, declared, seenKeys, stat);
                currentTitle = title;
                body = new StringBuilder();
            } else {
                body.append(line).append('\n');
            }
        }
        collect(currentTitle, body, chapters, declared, seenKeys, stat);

        // 首个标题之前的有效正文 → 前言章节
        if (!headerBody.isEmpty()) {
            String content = joinTrim(headerBody);
            if (!content.isEmpty() && seenKeys.add("前言\u0001" + content.hashCode())) {
                chapters.add(0, new ChapterDraft("前言", content));
            }
        }

        // 正文全部为空的极端文件：退回保留标题，避免直接判定"未解析到章节"
        if (chapters.isEmpty() && !declared.isEmpty()) {
            chapters.addAll(declared);
            stat.empty = 0;
        }
        return new Parsed(bookName, author, chapters, stat.empty, stat.duplicate);
    }

    /** 章节统计 */
    private static final class ChapterStat {
        int empty;
        int duplicate;
    }

    /** 收尾一章：空章节丢弃，整章重复（标题+正文相同）也丢弃 */
    private static void collect(String title, StringBuilder body, List<ChapterDraft> chapters,
                                List<ChapterDraft> declared, Set<String> seenKeys, ChapterStat stat) {
        if (title == null) {
            return;
        }
        String content = body == null ? "" : body.toString().trim();
        declared.add(new ChapterDraft(title, content));
        if (content.isEmpty()) {
            stat.empty++;
            return;
        }
        String key = title + "\u0001" + content.hashCode();
        if (!seenKeys.add(key)) {
            stat.duplicate++;
            return;
        }
        chapters.add(new ChapterDraft(title, content));
    }

    private static String joinTrim(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    /** 标题规范化：压缩连续空白，去掉首尾空白与包裹符号 */
    private static String cleanTitle(String line) {
        String title = line.replaceAll("\\s{2,}", " ").trim();
        while (title.endsWith("-") || title.endsWith("—") || title.endsWith("·")) {
            title = title.substring(0, title.length() - 1).trim();
        }
        return title;
    }

    private static boolean isNoise(String line) {
        for (String keyword : NOISE_KEYWORDS) {
            if (line.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 行规范化：全角空格/制表符转半角空格，剔除 BOM 与零宽字符 */
    private static String normalize(String line) {
        if (line == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\u3000' || c == '\u00A0' || c == '\t' || c == '\u000B' || c == '\f') {
                sb.append(' ');
            } else if (c == '\uFEFF' || c == '\u0000' || c == '\u200B') {
                // 丢弃
            } else if (c == '\r') {
                // 丢弃
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        int start = 0;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (start <= length) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
