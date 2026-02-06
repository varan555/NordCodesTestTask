package com.example.auth;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;

@Epic("Authentication")
@Feature("Token Validation")
@Tag("auth")
@Tag("validation")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TokenValidationTest extends TestBase {

    @BeforeAll
    void setupWireMock() {
        Allure.step("Настройка WireMock для всех тестов", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            Allure.addAttachment("Конфигурация мока", "text/plain", "/auth → 200 OK");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ABCDEF1234567890ABCDEF1234567890",  // Все заглавные буквы и цифры
            "1234567890ABCDEF1234567890ABCDEF",  // Начинается с цифр
            "00000000000000000000000000000000",  // Все нули
    })
    @Tag("019")
    @DisplayName("Валидные форматы токенов: {arguments}")
    @Severity(CRITICAL)
    void validTokenFormats(String token) {
        Allure.step("Тестирование валидного токена: " + token, () -> {
            Allure.addAttachment("Токен (валидный)", "text/plain", token);
            Allure.addAttachment("Характеристики", "text/plain",
                    "Длина: " + token.length() + " символов\n" +
                            "Формат: HEX (0-9, A-F)\n" +
                            "Регистр: верхний");

            Allure.step("1. LOGIN с валидным токеном", () -> {
                Allure.addAttachment("Ожидание", "text/plain", "Должен вернуть 200 OK");

                given()
                        .formParam("token", token)
                        .formParam("action", "LOGIN")
                        .when()
                        .post("/endpoint")
                        .then()
                        .statusCode(200)
                        .body("result", org.hamcrest.Matchers.equalTo("OK"));

                Allure.addAttachment("Результат", "text/plain",
                        "✓ LOGIN успешен с токеном длиной " + token.length() + " символов");
            });

            Allure.step("2. LOGOUT с валидным токеном", () -> {
                Allure.addAttachment("Ожидание", "text/plain", "Должен вернуть 200 OK");

                given()
                        .formParam("token", token)
                        .formParam("action", "LOGOUT")
                        .when()
                        .post("/endpoint")
                        .then()
                        .statusCode(200)
                        .body("result", org.hamcrest.Matchers.equalTo("OK"));

                Allure.addAttachment("Результат", "text/plain",
                        "✓ LOGOUT успешен\n✓ Токен полностью поддерживается системой");
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdef1234567890abcdef1234567890",  // строчные буквы
            "ПРИВЕТ1234567890ПРИВЕТ1234567890",  // кирилица
            "你好1234567890你好1234567890你好12",  // иероглифы
            "ABCDEF1234567890ABCDEF123456789",   // 31 символ
            "ABCDEF1234567890ABCDEF12345678901", // 33 символа
            "ABCD EF1234567890ABCDEF123456789",  // содержит пробел
            "ABCD-EF1234567890ABCDEF123456789",  // содержит дефис
            "ABCDEF1234567890ABCDEF123456789!",  // содержит спецсимвол
            "",                                  // пустая строка
    })
    @Tag("020")
    @DisplayName("Невалидные форматы токенов: {arguments}")
    @Severity(CRITICAL)
    void invalidTokenFormats(String token) {
        Allure.step("Тестирование невалидного токена", () -> {
            Allure.addAttachment("Токен (невалидный)", "text/plain", token);
            Allure.addAttachment("Причина невалидности", "text/plain",
                    "Длина: " + token.length() + " символов\n" +
                            "Формат: " + analyzeTokenIssue(token));

            Allure.step("LOGIN с невалидным токеном", () -> {
                Allure.addAttachment("Ожидание", "text/plain",
                        "Должен вернуть 400 Bad Request");

                given()
                        .formParam("token", token)
                        .formParam("action", "LOGIN")
                        .when()
                        .post("/endpoint")
                        .then()
                        .statusCode(400)
                        .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

                Allure.addAttachment("Результат", "text/plain",
                        "✓ LOGIN отклонен с кодом 400\n" +
                                "✓ Токен " + getTokenDescription(token) + " не принят системой\n" +
                                "✓ Валидация формата работает корректно");
            });
        });
    }

    @Test
    @Tag("021")
    @DisplayName("Токен со спецсимволами SQL инъекции")
    @Severity(CRITICAL)
    void tokenWithSqlInjection() {
        Allure.description("Проверка защиты от SQL инъекций через токен");

        String[] sqlInjectionTokens = {
                "' OR '1'='1",
                "'; DROP TABLE users; --",
                "' UNION SELECT * FROM users --",
                "admin' --"
        };

        for (String token : sqlInjectionTokens) {
            // Дополняем до 32 символов
            String fullToken = token + "A".repeat(Math.max(0, 32 - token.length()));

            Allure.step("Тестирование токена с SQL инъекцией: " + token, () -> {
                Allure.addAttachment("Исходный payload", "text/plain", token);
                Allure.addAttachment("Токен для отправки", "text/plain", fullToken);
                Allure.addAttachment("Тип инъекции", "text/plain",
                        getSqlInjectionType(token));

                Allure.step("LOGIN с токеном SQL инъекции", () -> {
                    Allure.addAttachment("Ожидание", "text/plain",
                            "Система должна отклонить запрос с кодом 400");

                    given()
                            .formParam("token", fullToken)
                            .formParam("action", "LOGIN")
                            .when()
                            .post("/endpoint")
                            .then()
                            .statusCode(400)
                            .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

                    Allure.addAttachment("Результат", "text/plain",
                            "✓ SQL инъекция '" + token + "' отклонена\n" +
                                    "✓ Защита от инъекций работает\n" +
                                    "✓ Статус 400 (Bad Request)");
                });
            });
        }

        Allure.addAttachment("Итог защиты от SQL инъекций", "text/plain",
                "✓ Все типы SQL инъекций отклонены:\n" +
                        "  - SQL комментарии (--)\n" +
                        "  - Объединение запросов (UNION)\n" +
                        "  - Условные конструкции (OR '1'='1')\n" +
                        "  - Удаление данных (DROP TABLE)\n" +
                        "✓ Валидация входных данных предотвращает SQL инъекции");
    }

    @Test
    @Tag("022")
    @DisplayName("Токен с XSS payload")
    @Severity(CRITICAL)
    void tokenWithXssPayload() {
        Allure.description("Проверка защиты от XSS атак через токен");

        String xssToken = "<script>alert('xss')</script>ABC";

        Allure.step("Подготовка XSS payload", () -> {
            Allure.addAttachment("XSS payload", "text/plain", xssToken);
            Allure.addAttachment("Тип атаки", "text/plain", "Cross-Site Scripting (XSS)");
            Allure.addAttachment("Опасность", "text/plain",
                    "Может выполнить произвольный JavaScript код в браузере");
        });

        Allure.step("LOGIN с XSS токеном", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна отклонить запрос с кодом 400");

            given()
                    .formParam("token", xssToken)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ XSS инъекция '<script>alert(...)</script>' отклонена\n" +
                            "✓ Защита от XSS работает\n" +
                            "✓ HTML/JavaScript теги не допускаются в токенах");
        });

        Allure.addAttachment("Защита от XSS", "text/plain",
                "✓ Токены валидируются на наличие HTML/JavaScript тегов\n" +
                        "✓ Предотвращено выполнение произвольного кода\n" +
                        "✓ Защита на уровне валидации входных данных");
    }

    // Вспомогательные методы для генерации описаний
    private String analyzeTokenIssue(String token) {
        if (token.isEmpty()) return "Пустая строка";
        if (token.length() != 32) return "Некорректная длина: " + token.length() + " (должно быть 32)";
        if (token.matches(".*[a-z].*")) return "Содержит строчные буквы (должны быть заглавные)";
        if (!token.matches("^[0-9A-F]*$")) return "Содержит недопустимые символы (допустимы только 0-9, A-F)";
        return "Неизвестная проблема";
    }

    private String getTokenDescription(String token) {
        if (token.isEmpty()) return "[пустая строка]";
        if (token.length() < 32) return "[" + token + "] (слишком короткий)";
        if (token.length() > 32) return "[" + token.substring(0, 20) + "...] (слишком длинный)";
        return "[" + token + "]";
    }

    private String getSqlInjectionType(String payload) {
        if (payload.contains("DROP")) return "SQL DROP инъекция";
        if (payload.contains("UNION")) return "SQL UNION инъекция";
        if (payload.contains("OR '1'='1")) return "SQL conditional инъекция";
        if (payload.contains("--")) return "SQL comment инъекция";
        return "SQL инъекция";
    }
}