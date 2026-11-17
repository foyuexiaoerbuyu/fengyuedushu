package xyz.fycz.myreader.widget.dialogx.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import xyz.fycz.myreader.widget.dialogx.DialogX;
import xyz.fycz.myreader.widget.dialogx.interfaces.BaseDialog;
import xyz.fycz.myreader.widget.dialogx.interfaces.OnBindView;

/**
 * 全屏对话框，用于输入框、网页浏览等自定义全屏布局场景。
 */
@SuppressWarnings("unchecked")
public class FullScreenDialog extends BaseDialog<FullScreenDialog> {

    private OnBindView<FullScreenDialog> onBindView;

    private boolean showPending;

    public FullScreenDialog() {
        super();
    }

    public static FullScreenDialog show(OnBindView<FullScreenDialog> onBindView) {
        FullScreenDialog dialog = new FullScreenDialog();
        dialog.onBindView = onBindView;
        dialog.show();
        return dialog;
    }

    public FullScreenDialog setCustomView(OnBindView<FullScreenDialog> onBindView) {
        this.onBindView = onBindView;
        return this;
    }

    @Override
    public FullScreenDialog show() {
        MAIN.post(this::buildAndShow);
        showPending = true;
        return this;
    }

    private void buildAndShow() {
        if (!showPending) {
            return;
        }
        showPending = false;
        Context context = getContext();
        Activity activity = DialogX.getActivity();
        if (activity != null && !activity.isFinishing()) {
            context = activity;
        }
        if (context == null || onBindView == null) {
            return;
        }
        Dialog d = new Dialog(context);
        View contentView = LayoutInflater.from(context).inflate(onBindView.getLayoutId(), null);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(contentView);
        Window window = d.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);
        }
        safeShow(d);
        bindOnBackPressed();
        onBindView.onBind(this, contentView);
    }
}
