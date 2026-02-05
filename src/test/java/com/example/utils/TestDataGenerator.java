package com.example.utils;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Утилитарный класс для генерации тестовых данных
 */
public class TestDataGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String VALID_CHARS = "0123456789ABCDEF";
    private static final String ALL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";

    /**
     * Генерирует валидный токен (32 символа A-Z0-9)
     */
    public static String generateValidToken() {
        return generateToken(32, VALID_CHARS);
    }

    /**
     * Генерирует токен указанной длины
     */
    public static String generateToken(int length, String characters) {
        StringBuilder token = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            token.append(characters.charAt(RANDOM.nextInt(characters.length())));
        }
        return token.toString();
    }

    /**
     * Генерирует невалидный токен (неправильные символы)
     */
    public static String generateInvalidToken() {
        // Генерируем строку из недопустимых символов
        String invalidChars = ALL_CHARS;
        // Удаляем допустимые символы
        for (char c : VALID_CHARS.toCharArray()) {
            invalidChars = invalidChars.replace(String.valueOf(c), "");
        }
        return generateToken(32, invalidChars);
    }

    /**
     * Генерирует токен неправильной длины
     */
    public static String generateWrongLengthToken(int length) {
        return generateToken(length, VALID_CHARS);
    }

    /**
     * Генерирует список валидных токенов
     */
    public static List<String> generateValidTokens(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> generateValidToken())
                .collect(Collectors.toList());
    }

    /**
     * Генерирует SQL инъекции для тестирования
     */
    public static List<String> generateSqlInjectionPayloads() {
        return Arrays.asList(
                "' OR '1'='1",
                "'; DROP TABLE users; --",
                "' UNION SELECT * FROM users --",
                "admin' --",
                "' OR 'a'='a",
                "') OR ('1'='1",
                "' OR 1=1--",
                "' OR '1'='1' --",
                "' OR '1'='1' /*",
                "' OR '1'='1' #"
        );
    }

    /**
     * Генерирует XSS payloads для тестирования
     */
    public static List<String> generateXssPayloads() {
        return Arrays.asList(
                "<script>alert('XSS')</script>",
                "<img src=x onerror=alert('XSS')>",
                "<svg onload=alert('XSS')>",
                "\"><script>alert('XSS')</script>",
                "javascript:alert('XSS')",
                "onmouseover=alert('XSS')",
                "<body onload=alert('XSS')>",
                "<iframe src=javascript:alert('XSS')>"
        );
    }

    /**
     * Генерирует пограничные значения для длины токена
     */
    public static Map<String, String> generateBoundaryTokens() {
        Map<String, String> tokens = new LinkedHashMap<>();

        // Граничные значения вокруг 32 символов
        tokens.put("31_characters", generateWrongLengthToken(31));
        tokens.put("32_characters_valid", generateValidToken()); // 32 символа
        tokens.put("33_characters", generateWrongLengthToken(33));
        tokens.put("0_characters", "");
        tokens.put("1_character", generateWrongLengthToken(1));
        tokens.put("255_characters", generateWrongLengthToken(255));
        tokens.put("1000_characters", generateWrongLengthToken(1000));

        return tokens;
    }

    /**
     * Генерирует токены с разными комбинациями символов
     */
    public static Map<String, String> generateCharacterCombinationTokens() {
        Map<String, String> tokens = new LinkedHashMap<>();

        // Только буквы
        tokens.put("only_letters", "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEF");

        // Только цифры
        tokens.put("only_numbers", "12345678901234567890123456789012");

        // Чередование букв и цифр
        tokens.put("alternating", "A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6");

        // Все A
        tokens.put("all_A", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        // Все Z (максимальная буква)
        tokens.put("all_Z", "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ");

        // Все 0
        tokens.put("all_0", "00000000000000000000000000000000");

        // Все 9 (максимальная цифра)
        tokens.put("all_9", "99999999999999999999999999999999");

        return tokens;
    }

    /**
     * Генерирует токены с Unicode символами
     */
    public static Map<String, String> generateUnicodeTokens() {
        Map<String, String> tokens = new LinkedHashMap<>();

        // Русские буквы
        tokens.put("russian", "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ");

        // Китайские иероглифы
        tokens.put("chinese", "的一是不了在人我有他这为之大来以个中上们");

        // Emoji
        tokens.put("emoji", "😀😃😄😁😆😅😂🤣☺️😊😇🙂🙃😉😌😍🥰😘😗😙😚");

        // Спецсимволы
        tokens.put("special_chars", "!@#$%^&*()_+-=[]{}|;:,.<>?/~`\"'\\");

        return tokens;
    }

    /**
     * Генерирует токены для тестирования безопасности
     */
    public static Map<String, String> generateSecurityTestTokens() {
        Map<String, String> tokens = new LinkedHashMap<>();

        // Null byte injection
        tokens.put("null_byte", "ABCDEF\0GHIJKL\0MNOPQR\0STUVWX\0YZ");

        // CRLF injection
        tokens.put("crlf_injection", "ABCDEF\r\nGHIJKL\r\nMNOPQR\r\nSTUVWXYZ");

        // Path traversal
        tokens.put("path_traversal", "../../../etc/passwdABCDEFGHIJKLM");

        // Command injection
        tokens.put("command_injection", "ABCDEF; rm -rf / ;GHIJKLMNOPQRSTUV");

        // Very long string (может вызвать buffer overflow)
        tokens.put("very_long", "A".repeat(10000));

        // Binary data
        byte[] binaryData = new byte[32];
        RANDOM.nextBytes(binaryData);
        tokens.put("binary_data", new String(binaryData));

        return tokens;
    }

    /**
     * Генерирует действия для тестирования
     */
    public static List<String> generateActions() {
        return Arrays.asList("LOGIN", "ACTION", "LOGOUT");
    }

    /**
     * Генерирует невалидные действия
     */
    public static List<String> generateInvalidActions() {
        return Arrays.asList(
                "login",        // нижний регистр
                "Login",        // смешанный регистр
                "LOGINN",       // опечатка
                "LOGOUTT",      // опечатка
                "ACT",          // сокращение
                "",             // пустая строка
                "   ",          // пробелы
                "LOGIN ACTION", // два действия
                "LOGIN\0",      // null byte
                "<script>",     // XSS
                "' OR '1'='1"   // SQL injection
        );
    }

    /**
     * Генерирует API ключи для тестирования
     */
    public static List<String> generateApiKeys() {
        return Arrays.asList(
                "qazWSXedc",        // правильный
                "QAZWSXEDC",        // верхний регистр
                "qazwsxedc",        // нижний регистр
                "qazWSXedc ",       // с пробелом в конце
                " qazWSXedc",       // с пробелом в начале
                "qazWSXedc\t",      // с табуляцией
                "",                 // пустой
                "   ",              // пробелы
                "qazWSXedc1",       // правильный + цифра
                "qazWSXedc!",       // правильный + спецсимвол
                "qazWSXedc" + "A".repeat(1000) // очень длинный
        );
    }
}