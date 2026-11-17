package xyz.fycz.myreader.widget.dialogx;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * 轻量对话框全局配置与上下文管理。
 * 替代原 com.kongzue.dialogx 依赖，仅保留本项目实际使用的能力。
 */
public class DialogX {

    public enum THEME {
        AUTO, LIGHT, DARK
    }

    public static boolean DEBUGMODE = false;

    public static Object globalStyle;

    public static THEME globalTheme = THEME.AUTO;

    private static Application application;

    private static WeakReference<Activity> topActivity = new WeakReference<>(null);

    private static Context fallbackContext;

    public static void init(Application application) {
        DialogX.application = application;
        fallbackContext = application;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                topActivity = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (topActivity.get() == activity && !activity.isFinishing()) {
                    // 保留引用，避免弹窗时取不到上下文
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (topActivity.get() == activity) {
                    topActivity = new WeakReference<>(null);
                }
            }
        });
    }

    /**
     * 获取当前可用的 Activity 上下文，取不到时退化为 Application 上下文。
     */
    public static Context getContext() {
        Activity activity = topActivity.get();
        if (activity != null && !activity.isFinishing()) {
            return activity;
        }
        return fallbackContext;
    }

    public static Activity getActivity() {
        return topActivity.get();
    }

    public static void setTopActivity(Activity activity) {
        if (activity != null) {
            topActivity = new WeakReference<>(activity);
        }
    }
}
