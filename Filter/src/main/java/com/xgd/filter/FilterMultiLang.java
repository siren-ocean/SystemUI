package com.xgd.filter;

import java.io.File;

/**
 * 过滤多语言
 * Created by Siren on 2022/3/27.
 */
public class FilterMultiLang {

    //需要被取代的标签
    private static String[] TAGS = new String[]{
            "af", "am", "ar", "as", "az",
            "be", "bg", "bn", "bs", "ca",
            "cs", "da", "el", "en", "es",
            "et", "eu", "fa", "fi", "fr",
            "gl", "gu", "hi", "hr", "hu",
            "hy", "in", "is", "it", "iw",
            "ja", "ka", "kk", "km", "kn",
            "ko", "ky", "lo", "lt", "lv",
            "mk", "ml", "mn", "mr", "ms",
            "my", "nb", "ne", "nl", "or",
            "pa", "pl", "pt", "ro", "rm",
            "ro", "ru", "si", "sk", "sl",
            "sq", "sr", "sv", "sw", "ta",
            "te", "th", "tl", "tr", "uk",
            "ur", "uz", "vi", "zu", "de",
            "zh-rHK", "zh-rTW", "b+sr+Latn"
    };

    /**
     * 执行过滤任务
     */
    public static void filter(String path) {
        File folder = new File(path);
        if (!folder.exists()) {
            System.out.println(folder.getPath() + "路径不存在");
            return;
        }
        filterFolder(folder);
    }

    private static void filterFolder(File folder) {
        File[] files = folder.listFiles();
        for (File file : files) {
            if (file.isDirectory() && isMultiLang(file.getName())) {
                deleteFile(file);
                System.out.println("Delete folder：" + file.getPath());
            }
        }
    }

    private static boolean isMultiLang(String name) {
        for (String tag : TAGS) {
            if (name.endsWith("-" + tag) || name.contains("-" + tag + "-")) {
                return true;
            }
        }
        return false;
    }

    private static void deleteFile(File dirFile) {
        if (dirFile.isFile()) {
            dirFile.delete();
            ShellUtils.ignoreFile(dirFile.getPath());//忽略文件
        } else {
            for (File file : dirFile.listFiles()) {
                deleteFile(file);
            }
            dirFile.delete();
        }
    }
}
