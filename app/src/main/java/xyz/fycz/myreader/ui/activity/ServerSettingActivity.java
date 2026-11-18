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

package xyz.fycz.myreader.ui.activity;

import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.viewbinding.ViewBinding;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import xyz.fycz.myreader.R;
import xyz.fycz.myreader.base.BaseActivity;
import xyz.fycz.myreader.util.ToastUtils;
import xyz.fycz.myreader.webapi.server.ServerApi;
import xyz.fycz.myreader.webapi.server.ServerAuthException;
import xyz.fycz.myreader.webapi.server.ServerConfig;
import xyz.fycz.myreader.webapi.server.ServerUser;

/**
 * 「本机书库」服务端连接设置。
 * <p>
 * 用于配置自建 fyreader-service 的地址与访问令牌，并提供连通性测试。
 *
 * @author fengyue
 */
public class ServerSettingActivity extends BaseActivity<ViewBinding> {

    private EditText etBaseUrl;
    private EditText etToken;
    private TextView tvStatus;
    private TextView tvAccount;
    private Button btnAccount;

    /** 当前登录用户，null 表示未登录（匿名浏览公开书库）。 */
    private ServerUser currentUser;

    @Override
    protected void bindView() {
        setContentView(R.layout.activity_server_setting);
        etBaseUrl = (EditText) findViewById(R.id.et_base_url);
        etToken = (EditText) findViewById(R.id.et_token);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvAccount = (TextView) findViewById(R.id.tv_account);
        btnAccount = (Button) findViewById(R.id.btn_account);
    }

    @Override
    protected void setUpToolbar(Toolbar toolbar) {
        toolbar.setTitle("本机书库");
    }

    @Override
    protected void initWidget() {
        super.initWidget();
        etBaseUrl.setText(ServerConfig.getBaseUrl());
        etToken.setText(ServerConfig.getToken());
    }

    @Override
    protected void initClick() {
        super.initClick();
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_test).setOnClickListener(v -> testConnect());
        findViewById(R.id.btn_reset).setOnClickListener(v -> resetConfig());
        btnAccount.setOnClickListener(v -> onAccountClicked());
        findViewById(R.id.btn_account_refresh).setOnClickListener(v -> refreshAccount());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccount();
    }

    private void save() {
        String url = etBaseUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            ToastUtils.showWarring("请填写服务端地址");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            ToastUtils.showWarring("地址需以 http:// 或 https:// 开头");
            return;
        }
        ServerConfig.setBaseUrl(url);
        ServerConfig.setToken(etToken.getText().toString().trim());
        // 回填规范化后的值（会去掉末尾多余的斜杠）
        etBaseUrl.setText(ServerConfig.getBaseUrl());
        etToken.setText(ServerConfig.getToken());
        ToastUtils.showSuccess("已保存，重新搜索即可生效");
    }

    private void testConnect() {
        // 先把当前输入落盘，保证测试用的就是界面上填的地址
        String url = etBaseUrl.getText().toString().trim();
        if (!TextUtils.isEmpty(url)) {
            ServerConfig.setBaseUrl(url);
            ServerConfig.setToken(etToken.getText().toString().trim());
        }
        tvStatus.setText("正在连接 " + ServerConfig.getBaseUrl() + " ...");
        addDisposable(ServerApi.health()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(msg -> tvStatus.setText("连接成功：" + msg + "\n" + ServerConfig.getBaseUrl()),
                        err -> tvStatus.setText("连接失败：" + (err.getMessage() == null
                                ? err.toString() : err.getMessage()))));
    }

    private void resetConfig() {
        ServerConfig.reset();
        etBaseUrl.setText(ServerConfig.getBaseUrl());
        etToken.setText(ServerConfig.getToken());
        tvStatus.setText("已恢复默认配置");
        applyAccount(null);
    }

    // ************************************************************
    // 服务端账号
    // ************************************************************

    /**
     * 刷新账号登录态：token 为空或服务端判定失效时显示「未登录」，匿名同样可以浏览公开书库。
     */
    private void refreshAccount() {
        if (tvAccount == null) {
            return;
        }
        if (TextUtils.isEmpty(ServerConfig.getToken())) {
            applyAccount(null);
            return;
        }
        tvAccount.setText("正在获取登录状态 ...");
        addDisposable(ServerApi.currentUser()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::applyAccount, err -> {
                    // token 可能是手工填写的共享口令，也可能是过期会话：统一按未登录展示
                    applyAccount(null);
                    if (err instanceof ServerAuthException) {
                        tvAccount.setText("登录已失效：请重新登录（未登录可浏览公开书库）");
                    }
                }));
    }

    private void applyAccount(ServerUser user) {
        currentUser = user;
        if (user == null) {
            tvAccount.setText("未登录：只能浏览公开书库（公开书无需登录）");
            btnAccount.setText("登录");
            return;
        }
        String nickname = TextUtils.isEmpty(user.getNickname()) ? user.getUsername() : user.getNickname();
        String username = TextUtils.isEmpty(user.getUsername()) ? "" : "（" + user.getUsername() + "）";
        tvAccount.setText("已登录：" + nickname + username);
        btnAccount.setText("退出登录");
    }

    private void onAccountClicked() {
        if (currentUser != null) {
            confirmLogout();
        } else {
            showLoginDialog();
        }
    }

    /**
     * 登录/注册对话框：一个用户名 + 一个密码输入框，登录成功后 token 自动写入 ServerConfig。
     */
    private void showLoginDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 20);
        container.setPadding(padding, padding / 2, padding, 0);

        final EditText etUsername = new EditText(this);
        etUsername.setHint("用户名");
        etUsername.setSingleLine(true);
        final EditText etPassword = new EditText(this);
        etPassword.setHint("密码");
        etPassword.setSingleLine(true);
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        container.addView(etUsername);
        container.addView(etPassword);

        new AlertDialog.Builder(this)
                .setTitle("登录服务端账号")
                .setView(container)
                .setPositiveButton("登录", (dialog, which) -> doLogin(
                        etUsername.getText().toString().trim(),
                        etPassword.getText().toString()))
                .setNeutralButton("注册", (dialog, which) -> doRegister(
                        etUsername.getText().toString().trim(),
                        etPassword.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void doLogin(String username, String password) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            ToastUtils.showWarring("请填写用户名和密码");
            return;
        }
        tvAccount.setText("正在登录 " + username + " ...");
        addDisposable(ServerApi.login(username, password)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(user -> {
                    etToken.setText(ServerConfig.getToken());
                    applyAccount(user);
                    ToastUtils.showSuccess("登录成功，已写入本机书库令牌");
                }, err -> {
                    applyAccount(null);
                    ToastUtils.showError("登录失败：" + messageOf(err));
                }));
    }

    private void doRegister(String username, String password) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            ToastUtils.showWarring("请填写用户名和密码");
            return;
        }
        tvAccount.setText("正在注册 " + username + " ...");
        addDisposable(ServerApi.register(username, password, null)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(user -> {
                    etToken.setText(ServerConfig.getToken());
                    applyAccount(user);
                    ToastUtils.showSuccess("注册成功，已自动登录");
                }, err -> {
                    applyAccount(null);
                    ToastUtils.showError("注册失败：" + messageOf(err));
                }));
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("退出后服务端会话立即失效，本机只保留匿名访问（可浏览公开书库）。若之前手工填写的是共享口令，也会一并清除。")
                .setPositiveButton("退出", (dialog, which) -> doLogout())
                .setNegativeButton("取消", null)
                .show();
    }

    private void doLogout() {
        addDisposable(ServerApi.logout()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ok -> {
                    etToken.setText(ServerConfig.getToken());
                    applyAccount(null);
                    ToastUtils.showSuccess("已退出登录");
                }, err -> {
                    // 即使服务端不可达，也认为本地已退出
                    etToken.setText(ServerConfig.getToken());
                    applyAccount(null);
                    ToastUtils.showWarring("本地已退出登录：" + messageOf(err));
                }));
    }

    private String messageOf(Throwable err) {
        if (err == null) {
            return "未知错误";
        }
        String message = err.getMessage();
        return TextUtils.isEmpty(message) ? err.toString() : message;
    }
}
