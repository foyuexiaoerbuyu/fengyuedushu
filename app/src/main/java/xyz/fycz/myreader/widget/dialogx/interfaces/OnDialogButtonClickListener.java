package xyz.fycz.myreader.widget.dialogx.interfaces;

import android.view.View;

/**
 * 对话框按钮点击回调。
 * 返回 true 表示消费本次点击（不自动关闭），false 表示按默认行为处理。
 */
public interface OnDialogButtonClickListener<D extends BaseDialog> {
    boolean onClick(D dialog, View v);
}
