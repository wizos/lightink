-ignorewarnings

#指定压缩级别
-optimizationpasses 5

#不跳过非公共的库的类成员
-dontskipnonpubliclibraryclassmembers

#混淆时采用的算法
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

#混淆类中的方法名
-useuniqueclassmembernames

#优化时允许访问并修改有修饰符的类和类的成员
-allowaccessmodification

#将文件来源重命名为“SourceFile”字符串
-renamesourcefileattribute SourceFile

#保留行号
-keepattributes SourceFile,LineNumberTable

#保持所有实现 Serializable 接口的类成员
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-dontwarn javax.annotation.**

-keep public class kotlin.reflect.jvm.internal.impl.** { public *; }

#WebView
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
   public *;
}
-keepclassmembers class * extends android.webkit.webViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.webViewClient {
    public void *(android.webkit.webView, jav.lang.String);
}

-dontwarn org.xmlpull.v1.XmlPullParser
-dontwarn org.xmlpull.v1.XmlSerializer
-keep class org.xmlpull.v1.* {*;}

#保持实体类
-keep class cn.lightink.reader.entity.** {*;}
-keep class cn.lightink.reader.model.** {*;}
-keep class cn.lightink.reader.module.booksource.** {*;}

-dontwarn com.google.android.material.**
-keep class com.google.android.material.** {*;}

-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

-dontwarn javax.xml.**
-keep class javax.xml.** { *; }
-keep interface javax.xml.** { *; }

-dontwarn com.jayway.jsonpath.**
-keep class com.jayway.jsonpath.** {*;}

#EventBus
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

#rhino.jar
-dontwarn org.mozilla.**
-keep class org.mozilla.** {*;}

-dontwarn org.slf4j.**
-keep class org.slf4j.** {*;}

-dontwarn com.google.gson.**
-keep class com.google.gson.** {*;}

#Retrofit(https://github.com/square/retrofit)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlin.Unit
-dontwarn retrofit2.-KotlinExtensions

#Okhttp(https://github.com/square/okhttp)
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.ConscryptPlatform

#Glide(https://github.com/bumptech/glide)
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

#WeChat
-keep class com.tencent.mm.opensdk.** {*;}
-keep class com.tencent.wxop.** {*;}
-keep class com.tencent.mm.sdk.** {*;}

#Weibo
-keep class com.sina.weibo.sdk.** { *; }

#Immersionbar
-keep class com.gyf.immersionbar.* {*;}
-dontwarn com.gyf.immersionbar.**

#UMeng
-keep class com.umeng.** {*;}
-keepclassmembers class * {
   public <init> (org.json.JSONObject);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep public class cn.lightink.reader.R$*{
public static final int *;
}

#QiNiu
-keep class com.qiniu.**{*;}
-keep class com.qiniu.**{public <init>();}
-ignorewarnings

#QuickJS
-dontwarn com.hippo.quickjs.**
-keep class com.hippo.quickjs.** {*;}

-keep class cn.lightink.reader.transcode.** {*;}
