package com.xgd.filter;

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
}
