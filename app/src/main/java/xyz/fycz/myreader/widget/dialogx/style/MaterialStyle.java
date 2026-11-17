package xyz.fycz.myreader.widget.dialogx.style;

/**
 * 样式占位类，仅用于兼容原 DialogX 的全局样式设置调用。
 * 系统对话框不使用该样式，保留接口以维持调用点不变。
 */
public class MaterialStyle {

    private static final MaterialStyle INSTANCE = new MaterialStyle();

    private MaterialStyle() {
    }

    public static MaterialStyle style() {
        return INSTANCE;
    }
}
