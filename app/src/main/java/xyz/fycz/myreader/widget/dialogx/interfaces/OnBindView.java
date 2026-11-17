package xyz.fycz.myreader.widget.dialogx.interfaces;

import android.view.View;

/**
 * 自定义布局绑定器：携带布局资源 ID，由对话框在显示前回调 onBind 完成视图初始化。
 */
public abstract class OnBindView<D extends BaseDialog> {

    private final int layoutResId;

    public OnBindView(int layoutResId) {
        this.layoutResId = layoutResId;
    }

    public int getLayoutId() {
        return layoutResId;
    }

    public abstract void onBind(D dialog, View v);
}
