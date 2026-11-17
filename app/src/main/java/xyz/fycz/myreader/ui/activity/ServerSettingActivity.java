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

import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.viewbinding.ViewBinding;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import xyz.fycz.myreader.R;
import xyz.fycz.myreader.base.BaseActivity;
import xyz.fycz.myreader.util.ToastUtils;
import xyz.fycz.myreader.webapi.server.ServerApi;
import xyz.fycz.myreader.webapi.server.ServerConfig;

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

    @Override
    protected void bindView() {
        setContentView(R.layout.activity_server_setting);
        etBaseUrl = (EditText) findViewById(R.id.et_base_url);
        etToken = (EditText) findViewById(R.id.et_token);
        tvStatus = (TextView) findViewById(R.id.tv_status);
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
    }
}
