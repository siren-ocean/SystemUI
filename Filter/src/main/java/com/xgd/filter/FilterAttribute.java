package com.xgd.filter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 过滤product特殊属性
 * Created by Siren on 2022/3/27.
 */
public class FilterAttribute {

    //需要被取代的标签
    private final static String TABLET = "product=\"tablet\"";
    private final static String DEVICE = "product=\"device\"";
    private final static String NOSDCARD = "product=\"nosdcard\"";
    private final static String EMULATOR = "product=\"emulator\"";
    private final static String TV = "product=\"tv\"";

    /**
     * 执行过滤任务
     */
    public static void filter(String path) {
        File folder = new File(path);
        if (!folder.exists()) {
            System.out.println(folder.getPath() + "路径不存在");
            return;
        }
        filterFile(folder);
    }

    /**
     * 匹配需要被移除的属性
     */
    private static boolean matchAttribute(String content) {
        String regex = ".*(" + TABLET + ")|(" + DEVICE + ")|(" + NOSDCARD + ")|(" + EMULATOR + ")|(" + TV + ").*";
        Matcher matcher = Pattern.compile(regex).matcher(content);
        return matcher.find();
    }

    private static void filterFile(File folder) {
        File[] files = folder.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                filterFile(file);
            } else if (file.getName().endsWith(".xml")) {
                String content = readFileToStr(file);
                List<String> filterList = getFieldListByRegex(content, "string");
                for (String s : filterList) {
                    content = content.replace(s, "");
                }
                if (filterList.size() > 0) {
                    writeToFile(file, content);
                    ShellUtils.ignoreFile(file.getPath());//忽略文件
                    System.out.println("Modified file：" + file.getPath());
                }
            }
        }
    }

    /**
     * 获取string标签字段并返回list
     */
    private static List<String> getFieldListByRegex(String xml, String label) {
        List<String> filterList = new ArrayList<>();
        String regex = "<" + label + "([\\s\\S]*?)</" + label + ">";
        Matcher matcher = Pattern.compile(regex).matcher(xml);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String content = xml.substring(start, end);
            if (matchAttribute(content)) {
                filterList.add(content);
            }
        }
        return filterList;
    }

    /**
     * 获取文件转String
     */
    private static String readFileToStr(File file) {
        StringBuffer buffer;
        try {
            FileInputStream is = new FileInputStream(file);
            buffer = streamToStr(is);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        return buffer.toString();
    }

    /**
     * inputStream to string
     */
    private static StringBuffer streamToStr(InputStream is) throws Exception {
        InputStreamReader reader = new InputStreamReader(is);
        BufferedReader bufferedReader = new BufferedReader(reader);
        StringBuffer buffer = new StringBuffer("");
        String str;
        while ((str = bufferedReader.readLine()) != null) {
            buffer.append(str);
            buffer.append("\n");
        }
        return buffer;
    }

    /**
     * write content
     */
    private static void writeToFile(File file, String content) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false)));
            writer.write(content);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (writer != null) try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
