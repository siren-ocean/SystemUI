package com.siren.filter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件处理工具
 * Created by Siren on 2025/8/2.
 */
public class FileContentReplacer {

    /**
     * 替换文件或目录中的指定字段
     */
    public static void replaceInPath(String path, String searchString, String replacement) {

        try {
            Path targetPath = Paths.get(path);

            if (Files.isRegularFile(targetPath)) {
                // 如果是单个文件，直接处理
                replaceInFile(targetPath, searchString, replacement);
            } else if (Files.isDirectory(targetPath)) {
                // 如果是目录，递归处理目录下的文件
                Files.walk(targetPath)
                        .filter(p -> Files.isRegularFile(p))
                        .forEach(p -> {
                            try {
                                replaceInFile(p, searchString, replacement);
                            } catch (IOException e) {
                                System.err.println("处理文件出错: " + p + " - " + e.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 在单个文件中替换指定字段
     */
    private static void replaceInFile(Path filePath, String searchString, String replacement) throws IOException {
        // 读取文件内容
        String content = new String(Files.readAllBytes(filePath));
        // 检查是否包含要查找的字段
        if (content.contains(searchString)) {
            System.out.println("正在处理文件: " + filePath);
            // 执行替换
            String newContent = content.replace(searchString, replacement);
            // 写回文件
            Files.write(filePath, newContent.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("已替换 " + countOccurrences(content, searchString) + " 处匹配项");
            ShellUtils.ignoreFile(System.getProperty("user.dir") + File.separator + filePath);
        }
    }

    /**
     * 计算字符串出现的次数
     */
    private static int countOccurrences(String content, String searchString) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(searchString, index)) != -1) {
            count++;
            index += searchString.length();
        }
        return count;
    }
}