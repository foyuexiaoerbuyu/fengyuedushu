package xyz.fycz.myreader.widget.dialogx.interfaces;

/**
 * 菜单项单选回调：点击后仅记录选中项，不立即关闭对话框。
 * 继承 OnMenuItemClickListener，便于直接传给 setOnMenuItemClickListener。
 */
public abstract class OnMenuItemSelectListener<D extends BaseDialog> implements OnMenuItemClickListener<D> {

    public abstract void onOneItemSelect(D dialog, CharSequence text, int which);

    @Override
    public boolean onClick(D dialog, CharSequence text, int which) {
        onOneItemSelect(dialog, text, which);
        return false;
    }
}
