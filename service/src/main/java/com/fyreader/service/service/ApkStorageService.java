package com.fyreader.service.service;

import com.fyreader.service.common.BusinessException;
import com.fyreader.service.config.FyReaderProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * APK 安装包存储服务。
 *
 * <p>安装包统一落在 {@code fyreader.apk-dir}（默认 {@code ./data/apk}），
 * 与 {@code GET /api/v1/app/apk} 的下载目录保持一致，上传后即可被客户端下载。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>只接受扩展名为 {@code .apk} 的文件，其余一律拒绝；</li>
 *   <li>服务端强制校验文件大小上限，防止超大文件占满磁盘；</li>
 *   <li>文件名做净化处理（剥离路径、过滤非法字符、去除隐藏前缀、限制长度），
 *       禁止 {@code ../} 等穿越写法落盘到目录之外；</li>
 *   <li>同名文件**不覆盖**，自动追加时间戳后缀另存，避免误覆盖正在分发的旧包。</li>
 * </ul>
 */
@Service
public class ApkStorageService {

    /** 单个安装包大小上限：200MB。 */
    private static final long MAX_APK_SIZE = 200L * 1024L * 1024L;

    private static final String APK_SUFFIX = ".apk";

    /** 落盘文件名主体最大长度，避免超出文件系统限制。 */
    private static final int MAX_NAME_LENGTH = 96;

    private static final String STATION_APK_URL = "/api/v1/app/apk";

    private final FyReaderProperties properties;

    public ApkStorageService(FyReaderProperties properties) {
        this.properties = properties;
    }

    /**
     * 保存上传的 APK。
     *
     * @return 文件名、字节数、可读大小、MD5、更新时间、站内下载地址
     */
    public Map<String, Object> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空");
        }
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            throw BusinessException.badRequest("缺少文件名，无法判断文件类型");
        }
        if (!original.toLowerCase(Locale.ROOT).endsWith(APK_SUFFIX)) {
            throw BusinessException.badRequest("只允许上传 .apk 安装包");
        }
        long size = file.getSize();
        if (size <= 0L) {
            throw BusinessException.badRequest("上传文件为空");
        }
        if (size > MAX_APK_SIZE) {
            throw BusinessException.badRequest("安装包超过大小上限 " + readableSize(MAX_APK_SIZE));
        }

        File dir = apkDir();
        File target = uniqueTarget(dir, sanitizeName(original));
        try {
            Files.createDirectories(dir.toPath());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(500, "保存安装包失败: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileName", target.getName());
        data.put("size", target.length());
        data.put("sizeText", readableSize(target.length()));
        data.put("md5", md5(target));
        data.put("lastModified", target.lastModified());
        data.put("downloadUrl", STATION_APK_URL);
        return data;
    }

    /**
     * 列出 apk-dir 下已有的安装包，按修改时间倒序，最新上传的排在首位。
     */
    public List<Map<String, Object>> list() {
        File dir = apkDir();
        List<Map<String, Object>> records = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) {
            return records;
        }
        List<File> apks = new ArrayList<>();
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(APK_SUFFIX)) {
                apks.add(f);
            }
        }
        apks.sort(Comparator.comparingLong(File::lastModified).reversed());
        for (int i = 0; i < apks.size(); i++) {
            File f = apks.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileName", f.getName());
            item.put("size", f.length());
            item.put("sizeText", readableSize(f.length()));
            item.put("lastModified", f.lastModified());
            item.put("current", i == 0);
            item.put("downloadUrl", STATION_APK_URL);
            records.add(item);
        }
        return records;
    }

    private File apkDir() {
        String dir = StringUtils.hasText(properties.getApkDir()) ? properties.getApkDir() : "./data/apk";
        return new File(dir).getAbsoluteFile();
    }

    /**
     * 文件名净化：剥离目录部分、过滤控制字符与文件系统保留字符、去掉隐藏文件前缀、限制长度。
     */
    private String sanitizeName(String raw) {
        String name = raw.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                continue;
            }
            if ("/\\:*?\"<>|".indexOf(c) >= 0) {
                sb.append('_');
                continue;
            }
            sb.append(c);
        }
        String cleaned = sb.toString().trim();
        if (cleaned.isEmpty()) {
            cleaned = "fyreader" + APK_SUFFIX;
        }
        if (cleaned.startsWith(".")) {
            cleaned = "apk" + cleaned;
        }
        if (!cleaned.toLowerCase(Locale.ROOT).endsWith(APK_SUFFIX)) {
            cleaned = cleaned + APK_SUFFIX;
        }
        String base = cleaned.substring(0, cleaned.length() - APK_SUFFIX.length());
        if (base.length() > MAX_NAME_LENGTH) {
            base = base.substring(0, MAX_NAME_LENGTH);
        }
        if (base.isEmpty()) {
            base = "fyreader";
        }
        return base + APK_SUFFIX;
    }

    /**
     * 同名文件不覆盖：追加时间戳后缀另存，极端并发下再追加序号兜底。
     */
    private File uniqueTarget(File dir, String fileName) {
        File target = new File(dir, fileName);
        if (!target.exists()) {
            return target;
        }
        String base = fileName.substring(0, fileName.length() - APK_SUFFIX.length());
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        target = new File(dir, base + "_" + stamp + APK_SUFFIX);
        int seq = 2;
        while (target.exists()) {
            target = new File(dir, base + "_" + stamp + "_" + seq + APK_SUFFIX);
            seq++;
        }
        return target;
    }

    private String md5(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream in = Files.newInputStream(file.toPath())) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    digest.update(buf, 0, n);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return "";
        }
    }

    private String readableSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.2f MB", mb);
    }
}
