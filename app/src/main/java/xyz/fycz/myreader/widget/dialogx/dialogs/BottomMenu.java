package xyz.fycz.myreader.widget.dialogx.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import xyz.fycz.myreader.widget.dialogx.DialogX;
import xyz.fycz.myreader.widget.dialogx.interfaces.BaseDialog;
import xyz.fycz.myreader.widget.dialogx.interfaces.OnDialogButtonClickListener;
import xyz.fycz.myreader.widget.dialogx.interfaces.OnMenuItemClickListener;
import xyz.fycz.myreader.widget.dialogx.interfaces.OnMenuItemSelectListener;

/**
 * 底部菜单，使用系统 AlertDialog 的列表能力实现。
 * 保留原 DialogX 的链式 API，调用点无需改动方法调用。
 */
@SuppressWarnings("unchecked")
public class BottomMenu extends BaseDialog<BottomMenu> {

    private CharSequence title;

    private final List<CharSequence> menuList = new ArrayList<>();

    private int selection = -1;

    private OnMenuItemClickListener<BottomMenu> onMenuItemClickListener;

    private OnMenuItemSelectListener<BottomMenu> onMenuItemSelectListener;

    private CharSequence cancelButtonText;

    private OnDialogButtonClickListener<BottomMenu> cancelButtonClickListener;

    private CharSequence okButtonText;

    private OnDialogButtonClickListener<BottomMenu> okButtonClickListener;

    private int buttonOrientation = LinearLayout.HORIZONTAL;

    public BottomMenu() {
        super();
    }

    public static BottomMenu build() {
        return new BottomMenu();
    }

    public static BottomMenu show(CharSequence[] items) {
        return build().setMenuList(Arrays.asList(items == null ? new CharSequence[0] : items)).show();
    }

    public static BottomMenu show(int titleResId, CharSequence[] items) {
        BottomMenu menu = build();
        menu.title = menu.getContext().getString(titleResId);
        menu.menuList.clear();
        if (items != null) {
            menu.menuList.addAll(Arrays.asList(items));
        }
        return menu.show();
    }

    public static BottomMenu show(CharSequence title, CharSequence[] items) {
        return build().setTitle(title).setMenuList(Arrays.asList(items == null ? new CharSequence[0] : items)).show();
    }

    public static BottomMenu show(CharSequence title, List<? extends CharSequence> items) {
        return build().setTitle(title).setMenuList(items).show();
    }

    public static BottomMenu show(CharSequence title, int itemsResId) {
        BottomMenu menu = build();
        menu.title = title;
        menu.menuList.clear();
        menu.menuList.addAll(Arrays.asList(menu.getContext().getResources().getTextArray(itemsResId)));
        return menu.show();
    }

    public BottomMenu setTitle(CharSequence title) {
        this.title = title;
        return this;
    }

    public BottomMenu setTitle(int titleResId) {
        Context c = getContext();
        this.title = c == null ? null : c.getString(titleResId);
        return this;
    }

    public BottomMenu setMenuStringList(CharSequence[] items) {
        menuList.clear();
        if (items != null) {
            menuList.addAll(Arrays.asList(items));
        }
        return this;
    }

    public BottomMenu setMenuStringList(String[] items) {
        menuList.clear();
        if (items != null) {
            menuList.addAll(Arrays.asList(items));
        }
        return this;
    }

    public BottomMenu setMenuStringList(List<? extends CharSequence> items) {
        menuList.clear();
        if (items != null) {
            menuList.addAll(items);
        }
        return this;
    }

    public BottomMenu setMenuList(List<? extends CharSequence> items) {
        return setMenuStringList(items);
    }

    public BottomMenu setMenuList(CharSequence[] items) {
        return setMenuStringList(items);
    }

    public BottomMenu setSelection(int selection) {
        this.selection = selection;
        return this;
    }

    public BottomMenu setOnMenuItemClickListener(OnMenuItemClickListener<BottomMenu> listener) {
        this.onMenuItemClickListener = listener;
        return this;
    }

    public BottomMenu setOnMenuItemSelectListener(OnMenuItemSelectListener<BottomMenu> listener) {
        this.onMenuItemSelectListener = listener;
        return this;
    }

    public BottomMenu setCancelButton(CharSequence text) {
        this.cancelButtonText = text;
        return this;
    }

    public BottomMenu setCancelButton(int textResId) {
        Context c = getContext();
        this.cancelButtonText = c == null ? null : c.getString(textResId);
        return this;
    }

    public BottomMenu setCancelButton(CharSequence text, OnDialogButtonClickListener<BottomMenu> listener) {
        this.cancelButtonText = text;
        this.cancelButtonClickListener = listener;
        return this;
    }

    public BottomMenu setCancelButton(int textResId, OnDialogButtonClickListener<BottomMenu> listener) {
        Context c = getContext();
        this.cancelButtonText = c == null ? null : c.getString(textResId);
        this.cancelButtonClickListener = listener;
        return this;
    }

    public BottomMenu setCancelButtonClickListener(OnDialogButtonClickListener<BottomMenu> listener) {
        this.cancelButtonClickListener = listener;
        return this;
    }

    public BottomMenu setOkButton(CharSequence text) {
        this.okButtonText = text;
        return this;
    }

    public BottomMenu setOkButton(int textResId) {
        Context c = getContext();
        this.okButtonText = c == null ? null : c.getString(textResId);
        return this;
    }

    public BottomMenu setOkButtonClickListener(OnDialogButtonClickListener<BottomMenu> listener) {
        this.okButtonClickListener = listener;
        return this;
    }

    public BottomMenu setButtonOrientation(int orientation) {
        this.buttonOrientation = orientation;
        return this;
    }

    public int getButtonOrientation() {
        return buttonOrientation;
    }

    public int getSelection() {
        return selection;
    }

    /**
     * 选中项仅记录、不关闭对话框，等底部按钮确认。
     */
    private boolean keepOpenOnSelect() {
        return onMenuItemSelectListener != null
                || onMenuItemClickListener instanceof OnMenuItemSelectListener;
    }

    @Override
    public BottomMenu show() {
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
        final CharSequence[] items = menuList.toArray(new CharSequence[0]);
        if (items.length > 0) {
            int checkedIndex = selection;
            if (checkedIndex >= items.length) {
                checkedIndex = -1;
            }
            if (checkedIndex >= 0 || keepOpenOnSelect()) {
                builder.setSingleChoiceItems(items, checkedIndex, (dialog, which) -> {
                    boolean keepOpen = false;
                    if (onMenuItemSelectListener != null) {
                        onMenuItemSelectListener.onOneItemSelect(BottomMenu.this, items[which], which);
                        keepOpen = true;
                    } else if (onMenuItemClickListener instanceof OnMenuItemSelectListener) {
                        ((OnMenuItemSelectListener<BottomMenu>) onMenuItemClickListener)
                                .onOneItemSelect(BottomMenu.this, items[which], which);
                        keepOpen = true;
                    } else if (onMenuItemClickListener != null) {
                        onMenuItemClickListener.onClick(BottomMenu.this, items[which], which);
                    }
                    if (!keepOpen && dialog != null) {
                        dialog.dismiss();
                    }
                });
            } else {
                builder.setItems(items, (dialog, which) -> {
                    if (onMenuItemClickListener != null) {
                        onMenuItemClickListener.onClick(BottomMenu.this, items[which], which);
                    }
                });
            }
        }
        if (cancelButtonClickListener != null) {
            CharSequence text = TextUtils.isEmpty(cancelButtonText) ? "确定" : cancelButtonText;
            builder.setPositiveButton(text, (dialog, which) ->
                    cancelButtonClickListener.onClick(BottomMenu.this, null));
        } else if (!TextUtils.isEmpty(cancelButtonText)) {
            builder.setNegativeButton(cancelButtonText, null);
        }
        if (okButtonClickListener != null) {
            CharSequence text = TextUtils.isEmpty(okButtonText) ? "确定" : okButtonText;
            builder.setPositiveButton(text, (dialog, which) ->
                    okButtonClickListener.onClick(BottomMenu.this, null));
        } else if (!TextUtils.isEmpty(okButtonText) && cancelButtonClickListener == null) {
            builder.setPositiveButton(okButtonText, null);
        }
        safeShow(builder.create());
        bindOnBackPressed();
    }
}
