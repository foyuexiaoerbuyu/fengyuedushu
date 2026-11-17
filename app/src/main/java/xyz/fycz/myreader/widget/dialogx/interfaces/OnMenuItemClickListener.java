package xyz.fycz.myreader.widget.dialogx.interfaces;

/**
 * 菜单项点击回调。
 */
public interface OnMenuItemClickListener<D extends BaseDialog> {
    boolean onClick(D dialog, CharSequence text, int which);
}
