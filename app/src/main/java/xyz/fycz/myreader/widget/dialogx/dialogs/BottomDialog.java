package xyz.fycz.myreader.widget.dialogx.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import xyz.fycz.myreader.widget.dialogx.DialogX;
import xyz.fycz.myreader.widget.dialogx.interfaces.BaseDialog;
import xyz.fycz.myreader.widget.dialogx.interfaces.OnBindView;
import xyz.fycz.myreader.widget.dialogx.interfaces.OnDialogButtonClickListener;

/**
 * 居中对话框，支持纯文本内容与自定义布局内容。
 * 使用系统 AlertDialog 承载，保留原 DialogX 的链式 API。
 */
@SuppressWarnings("unchecked")
public class BottomDialog extends BaseDialog<BottomDialog> {

    private CharSequence title;

    private CharSequence message;

    private OnBindView<BottomDialog> onBindView;

    private View customView;

    private CharSequence cancelButtonText;

    private OnDialogButtonClickListener<BottomDialog> cancelButtonClickListener;

    private CharSequence okButtonText;

    private OnDialogButtonClickListener<BottomDialog> okButtonClickListener;

    private int buttonOrientation = LinearLayout.HORIZONTAL;

    private boolean showPending;

    public BottomDialog() {
        super();
    }

    public BottomDialog(CharSequence title, CharSequence message) {
        super();
        this.title = title;
        this.message = message;
    }

    public static BottomDialog show(CharSequence title, CharSequence message) {
        BottomDialog dialog = new BottomDialog(title, message);
        dialog.showPending = true;
        dialog.show();
        return dialog;
    }

    public static BottomDialog show(CharSequence title, OnBindView<BottomDialog> onBindView) {
        BottomDialog dialog = new BottomDialog();
        dialog.title = title;
        dialog.onBindView = onBindView;
        dialog.showPending = true;
        dialog.show();
        return dialog;
    }

    public static BottomDialog show(OnBindView<BottomDialog> onBindView) {
        BottomDialog dialog = new BottomDialog();
        dialog.onBindView = onBindView;
        dialog.showPending = true;
        dialog.show();
        return dialog;
    }

    public static BottomDialog show(View view) {
        BottomDialog dialog = new BottomDialog();
        dialog.customView = view;
        dialog.showPending = true;
        dialog.show();
        return dialog;
    }

    public BottomDialog setTitle(CharSequence title) {
        this.title = title;
        return this;
    }

    public BottomDialog setTitle(int titleResId) {
        Context c = getContext();
        this.title = c == null ? null : c.getString(titleResId);
        return this;
    }

    public BottomDialog setMessage(CharSequence message) {
        this.message = message;
        return this;
    }

    public BottomDialog setMessage(int messageResId) {
        Context c = getContext();
        this.message = c == null ? null : c.getString(messageResId);
        return this;
    }

    public BottomDialog setCustomView(View view) {
        this.customView = view;
        return this;
    }

    public BottomDialog setCustomView(int layoutResId) {
        Context c = getContext();
        this.customView = c == null ? null : LayoutInflater.from(c).inflate(layoutResId, null);
        return this;
    }

    public BottomDialog setCustomView(OnBindView<BottomDialog> onBindView) {
        this.onBindView = onBindView;
        return this;
    }

    public View getCustomView() {
        return customView;
    }

    public BottomDialog setCancelButton(CharSequence text) {
        this.cancelButtonText = text;
        return this;
    }

    public BottomDialog setCancelButton(int textResId) {
        Context c = getContext();
        this.cancelButtonText = c == null ? null : c.getString(textResId);
        return this;
    }

    public BottomDialog setCancelButton(CharSequence text, OnDialogButtonClickListener<BottomDialog> listener) {
        this.cancelButtonText = text;
        this.cancelButtonClickListener = listener;
        return this;
    }

    public BottomDialog setCancelButtonClickListener(OnDialogButtonClickListener<BottomDialog> listener) {
        this.cancelButtonClickListener = listener;
        return this;
    }

    public BottomDialog setOkButton(CharSequence text) {
        this.okButtonText = text;
        return this;
    }

    public BottomDialog setOkButton(int textResId) {
        Context c = getContext();
        this.okButtonText = c == null ? null : c.getString(textResId);
        return this;
    }

    public BottomDialog setOkButtonClickListener(OnDialogButtonClickListener<BottomDialog> listener) {
        this.okButtonClickListener = listener;
        return this;
    }

    public BottomDialog setButtonOrientation(int orientation) {
        this.buttonOrientation = orientation;
        return this;
    }

    public int getButtonOrientation() {
        return buttonOrientation;
    }

    @Override
    public BottomDialog show() {
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
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (!TextUtils.isEmpty(title)) {
            builder.setTitle(title);
        }
        View contentView = customView;
        if (contentView == null && onBindView != null) {
            contentView = LayoutInflater.from(context).inflate(onBindView.getLayoutId(), null);
        }
        if (contentView != null) {
            builder.setView(contentView);
        } else if (!TextUtils.isEmpty(message)) {
            builder.setMessage(message);
        }
        if (cancelButtonClickListener != null) {
            CharSequence text = TextUtils.isEmpty(cancelButtonText) ? "取消" : cancelButtonText;
            builder.setNegativeButton(text, (dialog, which) ->
                    cancelButtonClickListener.onClick(BottomDialog.this, null));
        } else if (!TextUtils.isEmpty(cancelButtonText)) {
            builder.setNegativeButton(cancelButtonText, null);
        }
        if (okButtonClickListener != null) {
            CharSequence text = TextUtils.isEmpty(okButtonText) ? "确定" : okButtonText;
            builder.setPositiveButton(text, (dialog, which) ->
                    okButtonClickListener.onClick(BottomDialog.this, null));
        } else if (!TextUtils.isEmpty(okButtonText)) {
            builder.setPositiveButton(okButtonText, null);
        }
        safeShow(builder.create());
        bindOnBackPressed();
        if (onBindView != null && contentView != null) {
            onBindView.onBind(this, contentView);
        }
    }
}
