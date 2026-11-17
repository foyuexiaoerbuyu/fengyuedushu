package xyz.fycz.myreader.widget.dialogx.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.widget.LinearLayout;

import xyz.fycz.myreader.widget.dialogx.DialogX;
import xyz.fycz.myreader.widget.dialogx.interfaces.BaseDialog;
import xyz.fycz.myreader.widget.dialogx.interfaces.OnDialogButtonClickListener;

/**
 * 消息对话框，使用系统 AlertDialog 实现。
 * 同时支持"仅按钮"形态（标题与正文为空、仅两个操作按钮），对应原 MessageDialog(title, message, okText, cancelText)。
 */
@SuppressWarnings("unchecked")
public class MessageDialog extends BaseDialog<MessageDialog> {

    private CharSequence title;

    private CharSequence message;

    private CharSequence okButtonText;

    private CharSequence cancelButtonText;

    private OnDialogButtonClickListener<MessageDialog> okButtonClickListener;

    private OnDialogButtonClickListener<MessageDialog> cancelButtonClickListener;

    private int buttonOrientation = LinearLayout.HORIZONTAL;

    private boolean autoShow;

    public MessageDialog() {
        super();
    }

    public MessageDialog(CharSequence title, CharSequence message,
                         CharSequence okButtonText, CharSequence cancelButtonText) {
        super();
        this.title = title;
        this.message = message;
        this.okButtonText = okButtonText;
        this.cancelButtonText = cancelButtonText;
    }

    public MessageDialog(CharSequence title, CharSequence message, CharSequence okButtonText) {
        this(title, message, okButtonText, null);
    }

    public MessageDialog(Context context, CharSequence title, CharSequence message,
                         CharSequence okButtonText, CharSequence cancelButtonText, boolean autoShow) {
        super(context);
        this.title = title;
        this.message = message;
        this.okButtonText = okButtonText;
        this.cancelButtonText = cancelButtonText;
        this.autoShow = autoShow;
    }

    public static MessageDialog show(CharSequence title, CharSequence message) {
        MessageDialog dialog = new MessageDialog(title, message, null, null);
        dialog.show();
        return dialog;
    }

    public static MessageDialog show(CharSequence title, CharSequence message, CharSequence okButtonText) {
        MessageDialog dialog = new MessageDialog(title, message, okButtonText, null);
        dialog.show();
        return dialog;
    }

    public MessageDialog setTitle(CharSequence title) {
        this.title = title;
        return this;
    }

    public MessageDialog setTitle(int titleResId) {
        Context c = getContext();
        this.title = c == null ? null : c.getString(titleResId);
        return this;
    }

    public MessageDialog setMessage(CharSequence message) {
        this.message = message;
        return this;
    }

    public MessageDialog setMessage(int messageResId) {
        Context c = getContext();
        this.message = c == null ? null : c.getString(messageResId);
        return this;
    }

    public MessageDialog setOkButton(CharSequence text) {
        this.okButtonText = text;
        return this;
    }

    public MessageDialog setOkButton(int textResId) {
        Context c = getContext();
        this.okButtonText = c == null ? null : c.getString(textResId);
        return this;
    }

    public MessageDialog setCancelButton(CharSequence text) {
        this.cancelButtonText = text;
        return this;
    }

    public MessageDialog setCancelButton(int textResId) {
        Context c = getContext();
        this.cancelButtonText = c == null ? null : c.getString(textResId);
        return this;
    }

    public MessageDialog setOkButtonClickListener(OnDialogButtonClickListener<MessageDialog> listener) {
        this.okButtonClickListener = listener;
        return this;
    }

    public MessageDialog setCancelButtonClickListener(OnDialogButtonClickListener<MessageDialog> listener) {
        this.cancelButtonClickListener = listener;
        return this;
    }

    public MessageDialog setButtonOrientation(int orientation) {
        this.buttonOrientation = orientation;
        return this;
    }

    public int getButtonOrientation() {
        return buttonOrientation;
    }

    @Override
    public MessageDialog show() {
        MAIN.post(this::buildAndShow);
        return this;
    }

    private void buildAndShow() {
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
        if (!TextUtils.isEmpty(message)) {
            builder.setMessage(message);
        }
        if (okButtonClickListener != null) {
            CharSequence text = TextUtils.isEmpty(okButtonText) ? "确定" : okButtonText;
            builder.setPositiveButton(text, (dialog, which) ->
                    okButtonClickListener.onClick(MessageDialog.this, null));
        } else if (!TextUtils.isEmpty(okButtonText)) {
            builder.setPositiveButton(okButtonText, null);
        }
        if (cancelButtonClickListener != null) {
            CharSequence text = TextUtils.isEmpty(cancelButtonText) ? "取消" : cancelButtonText;
            builder.setNegativeButton(text, (dialog, which) ->
                    cancelButtonClickListener.onClick(MessageDialog.this, null));
        } else if (!TextUtils.isEmpty(cancelButtonText)) {
            builder.setNegativeButton(cancelButtonText, null);
        }
        safeShow(builder.create());
        bindOnBackPressed();
    }
}
