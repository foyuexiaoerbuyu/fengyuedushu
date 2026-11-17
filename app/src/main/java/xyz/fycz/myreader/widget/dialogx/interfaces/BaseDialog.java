package xyz.fycz.myreader.widget.dialogx.interfaces;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;

import xyz.fycz.myreader.widget.dialogx.DialogX;

/**
 * 对话框基类：提供链式 setter、显示与关闭能力。
 * 泛型 T 用于保证链式调用返回具体类型。
 */
@SuppressWarnings("unchecked")
public abstract class BaseDialog<T extends BaseDialog> {

    protected static final android.os.Handler MAIN =
            new android.os.Handler(android.os.Looper.getMainLooper());

    protected Context context;

    protected Dialog dialog;

    protected boolean cancelable = true;

    protected OnBackPressedListener onBackPressedListener;

    public BaseDialog() {
        this.context = DialogX.getContext();
    }

    public BaseDialog(Context context) {
        this.context = context != null ? context : DialogX.getContext();
    }

    public Context getContext() {
        return context != null ? context : DialogX.getContext();
    }

    /**
     * 显式记录当前可用的 Activity，供后续静态 show 使用。
     */
    public static void initActivityContext(android.app.Activity activity) {
        DialogX.setTopActivity(activity);
    }

    public T setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        if (dialog != null) {
            dialog.setCancelable(cancelable);
        }
        return (T) this;
    }

    public T setOnBackPressedListener(OnBackPressedListener listener) {
        this.onBackPressedListener = listener;
        return (T) this;
    }

    public T setMaskColor(int color) {
        return (T) this;
    }

    public boolean isShow() {
        return dialog != null && dialog.isShowing();
    }

    public Dialog getDialog() {
        return dialog;
    }

    /**
     * 显示对话框。子类每次调用都应重建 Dialog，避免复用已关闭实例。
     */
    public abstract T show();

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        dialog = null;
    }

    /**
     * 接管返回键：如果注册了 OnBackPressedListener 且其返回 false，则不关闭对话框。
     */
    protected void bindOnBackPressed() {
        if (dialog == null || onBackPressedListener == null) {
            return;
        }
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                    && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                boolean handled = onBackPressedListener.onBackPressed();
                return !handled;
            }
            return false;
        });
    }

    protected void safeShow(Dialog d) {
        this.dialog = d;
        d.setCancelable(cancelable);
        try {
            d.show();
        } catch (Exception e) {
            try {
                d.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    protected DialogInterface.OnClickListener wrap(OnDialogButtonClickListener<T> listener) {
        return (d, which) -> {
            if (listener != null) {
                listener.onClick((T) BaseDialog.this, null);
            }
        };
    }
}
