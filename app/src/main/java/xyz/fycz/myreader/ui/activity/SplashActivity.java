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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import xyz.fycz.myreader.R;
import xyz.fycz.myreader.application.App;
import xyz.fycz.myreader.base.BaseActivity;
import xyz.fycz.myreader.common.APPCONST;
import xyz.fycz.myreader.databinding.ActivitySplashBinding;
import xyz.fycz.myreader.greendao.service.BookGroupService;
import xyz.fycz.myreader.ui.dialog.MyAlertDialog;
import xyz.fycz.myreader.util.IOUtils;
import xyz.fycz.myreader.util.SharedPreUtils;
import xyz.fycz.myreader.util.ToastUtils;
import xyz.fycz.myreader.util.help.DateHelper;
import xyz.fycz.myreader.util.utils.ImageLoader;
import xyz.fycz.myreader.util.utils.MD5Utils;
import xyz.fycz.myreader.util.utils.OkHttpUtils;
import xyz.fycz.myreader.util.utils.SystemBarUtils;

public class SplashActivity extends BaseActivity<ActivitySplashBinding> {
    /*************Constant**********/
    public static final String TAG = SplashActivity.class.getSimpleName();
    public static int WAIT_INTERVAL = 0;

    private boolean hasStart = false;
    private boolean startToAd = false;
    private static final String INTENT_TO_AD = "startToAd";

    //连击/长按跳转
    private boolean isTripleClickJump = false;
    private boolean isLongPressJump = false;
    private boolean jumpTriggered = false;
    private int clickCount = 0;
    private long lastClickTime = 0;
    private static final long TRIPLE_CLICK_INTERVAL = 600;

    //创建子线程
    private Runnable start = () -> {
        if (!hasStart && !App.isDestroy(SplashActivity.this)) {
            hasStart = true;
            try {
                Thread.sleep(WAIT_INTERVAL);//使程序休眠
                Intent it = new Intent(SplashActivity.this, MainActivity.class);//启动MainActivity
                it.putExtra("startFromSplash", true);
                // 复用已存在的主界面，避免栈内出现重复实例
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(it);
                finish();//关闭当前活动
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, SplashActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.putExtra(INTENT_TO_AD, true);
        context.startActivity(intent);
    }

    /**
     * 是否开启了启动手势跳转（连击/长按任一）
     */
    public static boolean isGestureJumpEnabled(Context context) {
        SharedPreUtils preUtils = SharedPreUtils.getInstance();
        return preUtils.getBoolean("splashTripleClickJump", false)
                || preUtils.getBoolean("splashLongPressJump", false);
    }

    /**
     * 由主界面转发进入闪屏，等待用户手势后再进入主界面
     */
    public static void startForGesture(Context context) {
        Intent intent = new Intent(context, SplashActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void bindView() {
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 避免从桌面启动程序后，会重新实例化入口类的activity
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0 && !startToAd
                && !isGestureJumpEnabled(this)) {
            finish();
            return;
        }
        ImmersionBar.with(this)
                .fullScreen(true)
                .init();
        SystemBarUtils.hideStableStatusBar(this);
        //loadImage();
        boolean agreePrivacyPolicy = SharedPreUtils.getInstance().getBoolean("agreePrivacyPolicy");
        if (agreePrivacyPolicy) {
            requestPermission();
        } else {
            MyAlertDialog.showPrivacyDialog(this, (dialog, which) -> {
                SharedPreUtils.getInstance().putBoolean("agreePrivacyPolicy", true);
                requestPermission();
            }, (dialog, which) -> android.os.Process.killProcess(android.os.Process.myPid()));
        }
    }

    @Override
    protected boolean initSwipeBackEnable() {
        return false;
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
        startToAd = getIntent().getBooleanExtra(INTENT_TO_AD, false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void start() {
        startNoAd();
    }

    private void startNoAd() {
        Animation inAni = AnimationUtils.loadAnimation(SplashActivity.this, R.anim.fade_in);
        binding.ivSplash.setVisibility(View.VISIBLE);
        binding.ivSplash.startAnimation(inAni);
        WAIT_INTERVAL = 1500;
        loadImage();
        startNormal();
    }

    private void startNormal() {
        if (!App.isDestroy(this)) {
            if (startToAd) {
                finish();
                return;
            }
            isTripleClickJump = SharedPreUtils.getInstance().getBoolean("splashTripleClickJump", false);
            isLongPressJump = SharedPreUtils.getInstance().getBoolean("splashLongPressJump", false);
            if (isTripleClickJump || isLongPressJump) {
                initGestureJump();
            } else {
                doJump();
            }
        }
    }

    /**
     * 执行跳转（含私密书架校验）
     */
    private void doJump() {
        if (BookGroupService.getInstance().curGroupIsPrivate()) {
            App.runOnUiThread(() -> {
                MyAlertDialog.showPrivateVerifyDia(SplashActivity.this, needGoTo -> {
                    if (!hasStart)
                        (new Thread(start)).start();
                }, () -> {
                    SharedPreUtils.getInstance().putString(SplashActivity.this.getString(R.string.curBookGroupId), "");
                    SharedPreUtils.getInstance().putString(SplashActivity.this.getString(R.string.curBookGroupName), "");
                    if (!hasStart)
                        (new Thread(start)).start();
                });
            });
        } else {
            if (!hasStart)
                (new Thread(start)).start();
        }
    }

    /**
     * 开启连击/长按跳转后取消自动跳转，等待用户手势触发
     */
    private void initGestureJump() {
        View target = binding.getRoot();
        if (isTripleClickJump) {
            target.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - lastClickTime > TRIPLE_CLICK_INTERVAL)
                    clickCount = 0;
                clickCount++;
                lastClickTime = now;
                if (clickCount >= 3) {
                    clickCount = 0;
                    triggerJump();
                }
            });
        }
        if (isLongPressJump) {
            target.setOnLongClickListener(v -> {
                triggerJump();
                return true;
            });
        }
        if (isTripleClickJump && isLongPressJump)
            Toast.makeText(this, R.string.splash_jump_toast_both, Toast.LENGTH_SHORT).show();
        else if (isTripleClickJump)
            Toast.makeText(this, R.string.splash_jump_toast_triple, Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(this, R.string.splash_jump_toast_long, Toast.LENGTH_SHORT).show();
    }

    /**
     * 手势触发跳转，仅生效一次
     */
    private void triggerJump() {
        if (jumpTriggered)
            return;
        jumpTriggered = true;
        WAIT_INTERVAL = 200;
        doJump();
    }

    private void loadImage() {
        File imgFile = getFileStreamPath(APPCONST.FILE_NAME_SPLASH_IMAGE);
        SharedPreUtils preUtils = SharedPreUtils.getInstance();
        String splashImageMD5 = preUtils.getString("splashImageMD5");
        if (!imgFile.exists() || preUtils.getBoolean("needUdSI") ||
                !splashImageMD5.equals(MD5Utils.INSTANCE.getFileMD5s(imgFile, 16))) {
            if ("".equals(splashImageMD5)) return;
            downLoadImage();
            return;
        }
        String splashLoadDate = preUtils.getString("splashTime");
        if (splashLoadDate.equals("")) {
            return;
        }
        long startTime = 0;
        long endTime = 0;
        long curTime = DateHelper.getLongDate();
        if (splashLoadDate.contains("~")) {
            String[] splashLoadDates = splashLoadDate.split("~");
            startTime = DateHelper.strDateToLong(splashLoadDates[0] + " 00:00:00");
            endTime = DateHelper.strDateToLong(splashLoadDates[1] + " 00:00:00");
        }
        if (startTime == 0) {
            startTime = DateHelper.strDateToLong(splashLoadDate + " 00:00:00");
        }
        if (endTime == 0) {
            endTime = startTime + 24 * 60 * 60 * 1000;
        }
        if (curTime >= startTime && curTime <= endTime) {
            WAIT_INTERVAL = 1500;
            RequestOptions options = new RequestOptions()
                    .error(R.drawable.start)
                    .signature(new ObjectKey(splashLoadDate));
            ImageLoader.INSTANCE
                    .load(this, imgFile)
                    /*.error(R.drawable.start)
                    .signature(new ObjectKey(splashLoadDate))*/
                    .apply(options)
                    .into(binding.ivSplash);
        }
    }

    private void downLoadImage() {
        App.getApplication().newThread(() -> {
            String url = SharedPreUtils.getInstance().getString("splashImageUrl", "");
            if (!url.equals("")) {
                InputStream is = null;
                FileOutputStream fos = null;
                try {
                    is = OkHttpUtils.getInputStream(url);
                    fos = openFileOutput(APPCONST.FILE_NAME_SPLASH_IMAGE, MODE_PRIVATE);
                    byte[] bytes = new byte[1024];
                    int len;
                    while ((len = is.read(bytes)) != -1) {
                        fos.write(bytes, 0, len);
                    }
                    fos.flush();
                    Log.d("SplashActivity", "downLoadImage success!");
                } catch (Exception e) {
                    File data = getFileStreamPath(APPCONST.FILE_NAME_SPLASH_IMAGE);
                    if (data != null && data.exists()) {
                        data.delete();
                    }
                    e.printStackTrace();
                } finally {
                    IOUtils.close(is, fos);
                }
            }
        });
    }

    private void requestPermission() {
        //获取读取和写入SD卡的权限
        XXPermissions.with(this)
                /*.permission(new String[]{Permission.READ_PHONE_STATE,
                        Permission.WRITE_EXTERNAL_STORAGE,
                        Permission.READ_EXTERNAL_STORAGE,
                        Permission.ACCESS_FINE_LOCATION,
                        Permission.ACCESS_COARSE_LOCATION})*/
                .permission(APPCONST.STORAGE_PERMISSIONS)
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(List<String> permissions, boolean all) {
                        start();
                    }

                    @Override
                    public void onDenied(List<String> permissions, boolean never) {
                        if (permissions.contains(Permission.WRITE_EXTERNAL_STORAGE))
                            ToastUtils.showWarring("储存权限被拒绝，部分功能可能无法正常运行！");
                        start();
                    }
                });
    }
}
