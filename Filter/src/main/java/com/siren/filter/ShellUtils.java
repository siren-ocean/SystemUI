package com.siren.filter;

import java.io.File;

/**
 * shell脚本工具
 * Created by Siren on 2022/4/7.
 */
public class ShellUtils {

    /**
     * 忽略文件
     */
    public static void ignoreFile(String path) {
        String rootPath = System.getProperty("user.dir") + File.separator;

        execGit("git update-index --assume-unchanged " + (isWindows() ?
                path.substring(rootPath.length()).replace("\\", "\\\\") :
                path.substring(rootPath.length())));
    }

    public static boolean isWindows() {
        return System.getProperties().getProperty("os.name").toUpperCase().contains("WINDOWS");
    }

    /**
     * 执行git指令
     */
    private static void execGit(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
