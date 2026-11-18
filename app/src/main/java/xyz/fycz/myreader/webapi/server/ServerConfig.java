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

package xyz.fycz.myreader.webapi.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import xyz.fycz.myreader.application.App;
import xyz.fycz.myreader.common.APPCONST;

/**
 * 自建服务端（fyreader-service）连接配置。
 * <p>
 * 使用独立的 SharedPreferences 文件保存，避免与主配置混在一起，
 * 便于真机/模拟器在不同环境下切换服务端地址。
 *
 * @author fengyue
 */
public final class ServerConfig {

    private static final String PREFS_NAME = "fyreader_server";

    public static final String KEY_SERVER_ENABLED = "server_enabled";
    public static final String KEY_SOURCE_ENSURED = "server_source_ensured";
    public static final String KEY_SERVER_BASE_URL = "server_base_url";
    public static final String KEY_SERVER_TOKEN = "server_token";

    private ServerConfig() {
    }

    private static SharedPreferences prefs() {
        return App.getmContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 服务端模式开关。关闭时所有请求走原有书源链路。
     */
    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_SERVER_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_SERVER_ENABLED, enabled).apply();
    }

    /**
     * 服务端根地址，如 http://192.168.1.10:8088，末尾不带斜杠。
     */
    public static String getBaseUrl() {
        String url = prefs().getString(KEY_SERVER_BASE_URL, null);
        if (TextUtils.isEmpty(url)) {
            url = APPCONST.DEFAULT_SERVER_BASE_URL;
        }
        return trimTrailingSlash(url.trim());
    }

    public static void setBaseUrl(String baseUrl) {
        prefs().edit().putString(KEY_SERVER_BASE_URL, trimTrailingSlash(baseUrl == null ? "" : baseUrl.trim())).apply();
    }

    /**
     * 访问令牌，对应后端 fyreader.token（共享口令）或 /api/v1/auth/login 签发的会话 token。
     *
     * <p>未配置时返回空串，表示「匿名」：请求不带 X-FY-Token，服务端按匿名身份只返回公开书。
     * 早期版本在这里回退到硬编码口令 {@code fyreader-lite-token}，而服务端未配置同一口令时
     * 会把这个未知 token 判为无效并返回 401，导致未登录用户连公开书都读不了；
     * 因此改为匿名优先，需要共享口令或账号 token 时由设置页/登录明确写入。</p>
     */
    public static String getToken() {
        String token = prefs().getString(KEY_SERVER_TOKEN, null);
        return token == null ? "" : token.trim();
    }

    public static void setToken(String token) {
        prefs().edit().putString(KEY_SERVER_TOKEN, token == null ? "" : token.trim()).apply();
    }

    public static void reset() {
        prefs().edit().clear().apply();
    }

    /**
     * 「本机书库」书源是否已经自动注册过。
     * <p>
     * 只注册一次：注册成功后置为 true，用户之后在书源管理里把它删掉，
     * 下次启动也不会再被自动加回来。
     */
    public static boolean isSourceEnsured() {
        return prefs().getBoolean(KEY_SOURCE_ENSURED, false);
    }

    public static void setSourceEnsured(boolean ensured) {
        prefs().edit().putBoolean(KEY_SOURCE_ENSURED, ensured).apply();
    }

    /**
     * 拼接完整 API 地址。
     *
     * @param path 以 / 开头的路径，如 /books/1/chapters
     */
    public static String apiUrl(String path) {
        if (path == null || path.isEmpty()) {
            return getBaseUrl() + APPCONST.SERVER_API_PREFIX;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return getBaseUrl() + APPCONST.SERVER_API_PREFIX + p;
    }

    /**
     * 拼接完整 URL（不带 /api/v1 前缀），用于拼接服务端返回的相对资源地址。
     */
    public static String absoluteUrl(String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = getBaseUrl();
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    private static String trimTrailingSlash(String url) {
        String result = url;
        while (result != null && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result == null ? "" : result;
    }
}
