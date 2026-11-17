package xyz.fycz.myreader.widget.dialogx.interfaces;

/**
 * 对话框返回键监听。
 * 返回 true 表示已消费，false 表示交由对话框默认处理（关闭）。
 */
public interface OnBackPressedListener {
    boolean onBackPressed();
}
