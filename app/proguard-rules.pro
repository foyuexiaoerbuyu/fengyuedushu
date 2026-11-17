# ============================================================
# FYReader release 混淆/收缩规则（配合 R8 + proguard-android-optimize.txt）
# 仅 release 生效（debug 不开启 minify），用于 APK 瘦身第一批
# ============================================================

# ---------- 通用属性保留 ----------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- GreenDAO（含生成类与实体，反射实例化 + 表结构推导） ----------
-keep class org.greenrobot.greendao.** { *; }
-keep class xyz.fycz.myreader.greendao.** { *; }
-keepclassmembers class * extends org.greenrobot.greendao.AbstractDao { *; }
-keepclassmembers class * extends org.greenrobot.greendao.AbstractDaoSession { *; }
-dontwarn org.greenrobot.greendao.**

# ---------- Gson（本项目未使用 @SerializedName，依赖字段名反射，必须保留字段名） ----------
-keep class com.google.gson.** { *; }
# TypeToken 的匿名子类（object : TypeToken<T>() {}）承载泛型签名，一旦被收缩/合并，
# Gson 会在 $Gson$Types.getSupertype 处抛 Missing type parameter，必须完整保留
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class xyz.fycz.myreader.entity.** { <fields>; }
-keepclassmembers class xyz.fycz.myreader.model.** { <fields>; }
-keepclassmembers class xyz.fycz.myreader.greendao.entity.** { <fields>; }
-keepclassmembers class xyz.fycz.myreader.webapi.** { <fields>; }
-dontwarn com.google.gson.**

# ---------- 自建服务端接口模型（字段名与 JSON key 一一对应，且被 Gson 反射填充） ----------
-keep class xyz.fycz.myreader.webapi.server.** { *; }

# ---------- Rhino JS 引擎（书源 JS 规则，经 ScriptEngineManager 反射加载） ----------
-keep class org.mozilla.javascript.** { *; }
-keep class com.sun.script.javascript.** { *; }
-keepnames class com.sun.script.javascript.RhinoScriptEngineFactory
-adaptresourcefilecontents META-INF/services/javax.script.ScriptEngineFactory
-dontwarn org.mozilla.javascript.**
-dontwarn com.sun.script.javascript.**

# ---------- epublib（EPUB 解析） ----------
-keep class nl.siegmann.epublib.** { *; }
-dontwarn nl.siegmann.epublib.**
-dontwarn org.slf4j.**
-dontwarn org.xmlpull.**

# ---------- Jsoup / seimicrawler / jsonpath / antlr（书源规则解析） ----------
-keep class org.jsoup.** { *; }
-keep class org.seimicrawler.** { *; }
-keep class com.jayway.jsonpath.** { *; }
-keep class org.antlr.** { *; }
-dontwarn org.jsoup.**
-dontwarn org.seimicrawler.**
-dontwarn com.jayway.jsonpath.**
-dontwarn org.antlr.**
-dontwarn com.jayway.jsonpath.spi.**

# ---------- OkHttp / Okio ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# ---------- Maple（Hook 框架） ----------
-keep class me.fycz.maple.** { *; }
-dontwarn me.fycz.maple.**

# ---------- 其它三方兜底 ----------
-dontwarn com.bumptech.glide.**
-keep public class * implements com.bumptech.glide.module.GlideModule
-dontwarn io.reactivex.**
-dontwarn org.reactivestreams.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-dontwarn com.luhuiguo.chinese.**
-dontwarn com.zip4j.**
-dontwarn com.liulishuo.**
-dontwarn com.kongzue.**

# ---------- Kotlin 元数据（协程 / 反射依赖） ----------
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ---------- 枚举（values() / valueOf() 常被序列化与反射调用） ----------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- WebView JS 接口 ----------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------- native 方法 ----------
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------- 四大组件与系统组件 ----------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment

# ---------- 自定义 View 构造（布局 inflate 反射实例化） ----------
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keep public class * extends android.view.View {
    public void set*(...);
    public *** get*();
    public *** is*();
}

# ---------- Parcelable ----------
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ---------- Serializable ----------
-keepclassmembers class * implements java.io.Serializable {
    private static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    private java.lang.Object writeReplace();
    private java.lang.Object readResolve();
}

# ---------- 应用内反射入口 ----------
# LoadingIndicatorView 通过 Class.forName + newInstance 加载动画 Indicator
-keep class xyz.fycz.myreader.widget.loading.** { *; }
# 发现页 / 阅读器通过 Class.forName(source.getSourceUrl()) 加载数据源与爬虫
-keep class xyz.fycz.myreader.webapi.crawler.** { *; }
-keep class xyz.fycz.myreader.webapi.callback.** { *; }
-keepclassmembers class * implements xyz.fycz.myreader.webapi.crawler.base.ReadCrawler {
    public <init>(...);
}
# 注解与事件总线等编译期元信息
-keep @interface *
