package com.siren.filter;

import java.io.File;

/**
 * 主程序
 * Created by Siren on 2022/4/7.
 */
public class Main {

    /**
     * 执行过滤任务
     */
    public static void main(String[] args) {
        filterResource();
        replaceContent();
    }

    /**
     * 过滤资源文件
     */
    private static void filterResource() {
        String[] arr = new String[]{
                "res",
                "res-keyguard",
                "res-product",
                "SettingsLib/res",
                "WifiTrackerLib/res",
                "SettingsLib/HelpUtils/res",
                "SettingsLib/RestrictedLockUtils/res",
                "SettingsLib/SearchWidget/res"
        };

        for (String name : arr) {
            String path = System.getProperty("user.dir") + File.separator + name;
            // 可选项：清除多余的国际化语言，可提高编译效率
            FilterMultiLang.filter(path);
            // 必选项：清除string里面的product属性，如tablet、device等，因为AS无法识别该属性，会编译不通过
            FilterAttribute.filter(path);
        }
    }

    /**
     * 替换内容?androidprv:attr开头的内容，用透明色去填充
     */
    private static void replaceContent() {
        FileContentReplacer.replaceInPath("res", "\"?androidprv:attr/colorSurface\"", "\"@android:color/transparent\"");
        FileContentReplacer.replaceInPath("res/values/attrs.xml", "<attr name=\"backgroundColor\" format=\"integer\" />", "<attr name=\"backgroundColor\" />");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "<item name=\"android:colorBackground\">?androidprv:attr/colorSurface</item>", "<item name=\"android:colorBackground\">@android:color/transparent</item>");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "<item name=\"android:textColor\">?androidprv:attr/textColorOnAccent</item>", "<item name=\"android:textColor\">@android:color/transparent</item>");
        FileContentReplacer.replaceInPath("res/values/styles.xml", "<style name=\"AlertDialogStyle\" parent=\"@androidprv:style/AlertDialog.DeviceDefault\">", "<style name=\"AlertDialogStyle\" parent=\"@*android:style/AlertDialog.DeviceDefault\">");
    }
}
