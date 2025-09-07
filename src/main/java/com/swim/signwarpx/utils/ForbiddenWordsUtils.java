package com.swim.signwarpx.utils;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 違禁詞檢查工具類
 * 用於檢查傳送點名稱和群組名稱是否包含違禁詞
 */
public class ForbiddenWordsUtils {

    /**
     * 檢查文字是否包含違禁詞
     *
     * @param text   要檢查的文字
     * @param config 配置檔案
     * @return 如果包含違禁詞返回 true，否則返回 false
     */
    public static boolean containsForbiddenWords(String text, FileConfiguration config) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // 檢查功能是否啟用
        if (!config.getBoolean("forbidden-words.enabled", false)) {
            return false;
        }

        List<String> forbiddenWords = config.getStringList("forbidden-words.words");
        if (forbiddenWords.isEmpty()) {
            return false;
        }

        // 是否區分大小寫
        boolean caseSensitive = config.getBoolean("forbidden-words.case-sensitive", false);
        String textToCheck = caseSensitive ? text : text.toLowerCase();

        for (String forbiddenWord : forbiddenWords) {
            if (forbiddenWord == null || forbiddenWord.trim().isEmpty()) {
                continue;
            }

            String wordToCheck = caseSensitive ? forbiddenWord : forbiddenWord.toLowerCase();
            
            // 檢查是否為完整單詞匹配
            if (config.getBoolean("forbidden-words.whole-word-only", false)) {
                // 使用正則表達式檢查完整單詞
                String pattern = "\\b" + Pattern.quote(wordToCheck) + "\\b";
                if (Pattern.compile(pattern, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE)
                        .matcher(textToCheck).find()) {
                    return true;
                }
            } else {
                // 檢查是否包含該詞
                if (textToCheck.contains(wordToCheck)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 獲取第一個匹配的違禁詞（用於錯誤訊息顯示）
     *
     * @param text   要檢查的文字
     * @param config 配置檔案
     * @return 第一個匹配的違禁詞，如果沒有匹配則返回 null
     */
    public static String getFirstForbiddenWord(String text, FileConfiguration config) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // 檢查功能是否啟用
        if (!config.getBoolean("forbidden-words.enabled", false)) {
            return null;
        }

        List<String> forbiddenWords = config.getStringList("forbidden-words.words");
        if (forbiddenWords.isEmpty()) {
            return null;
        }

        // 是否區分大小寫
        boolean caseSensitive = config.getBoolean("forbidden-words.case-sensitive", false);
        String textToCheck = caseSensitive ? text : text.toLowerCase();

        for (String forbiddenWord : forbiddenWords) {
            if (forbiddenWord == null || forbiddenWord.trim().isEmpty()) {
                continue;
            }

            String wordToCheck = caseSensitive ? forbiddenWord : forbiddenWord.toLowerCase();
            
            // 檢查是否為完整單詞匹配
            if (config.getBoolean("forbidden-words.whole-word-only", false)) {
                // 使用正則表達式檢查完整單詞
                String pattern = "\\b" + Pattern.quote(wordToCheck) + "\\b";
                if (Pattern.compile(pattern, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE)
                        .matcher(textToCheck).find()) {
                    return forbiddenWord;
                }
            } else {
                // 檢查是否包含該詞
                if (textToCheck.contains(wordToCheck)) {
                    return forbiddenWord;
                }
            }
        }

        return null;
    }
}