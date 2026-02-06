package com.example.security;

import com.example.base.TestBase;
import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;

@Epic("Security")
@Feature("Access Control")
@Tag("security")
@Tag("authorization")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AccessControlTest extends TestBase {

    @BeforeAll
    void setupWireMock() {
        Allure.step("Настройка WireMock для всех тестов", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            wireMockServer.stubFor(post("/doAction").willReturn(ok()));
            Allure.addAttachment("Конфигурация моков", "text/plain",
                    "✓ /auth → 200 OK\n✓ /doAction → 200 OK");
        });
    }

    @ParameterizedTest(name = "HTTP {0} → status {1}")
    @CsvSource({
            "POST,    200, true",
            "GET,     405, false",
            "PUT,     405, true",
            "DELETE,  405, false",
            "PATCH,   405, true"
    })
    @Tag("040")
    @DisplayName("Проверка HTTP методов")
    void endpointHttpMethodsAccess(String method, int expectedStatus, boolean needsBody) {
        Allure.step("Тестирование HTTP метода " + method, () -> {
            Allure.addAttachment("Метод", "text/plain", method);
            Allure.addAttachment("Ожидаемый статус", "text/plain", String.valueOf(expectedStatus));
            Allure.addAttachment("Требует тело", "text/plain", needsBody ? "Да" : "Нет");

            String token = Allure.step("1. Генерация токена", () -> {
                String t = generateToken();
                Allure.addAttachment("Токен", "text/plain", t);
                return t;
            });

            Allure.step("2. Формирование запроса", () -> {
                RequestSpecification request = given()
                        .header("X-Api-Key", "qazWSXedc");

                if (needsBody) {
                    if ("POST".equals(method)) {
                        request.formParams(Map.of("token", token, "action", "LOGIN"));
                        Allure.addAttachment("Формат тела", "text/plain", "form-urlencoded");
                    } else {
                        request.contentType(ContentType.JSON)
                                .body(Map.of("token", token, "action", "LOGIN"));
                        Allure.addAttachment("Формат тела", "text/plain", "application/json");
                    }
                }

                Allure.step("3. Выполнение " + method + " запроса", () -> {
                    switch (method) {
                        case "GET" -> request.when().get("/endpoint");
                        case "POST" -> request.when().post("/endpoint");
                        case "PUT" -> request.when().put("/endpoint");
                        case "DELETE" -> request.when().delete("/endpoint");
                        case "PATCH" -> request.when().patch("/endpoint");
                    }
                });

                Allure.step("4. Проверка статуса ответа", () -> {
                    request.then().statusCode(expectedStatus);

                    String result = (expectedStatus == 200) ?
                            "✓ " + method + " разрешен (200 OK)" :
                            "✓ " + method + " запрещен (405 Method Not Allowed)";

                    Allure.addAttachment("Результат", "text/plain", result);
                });
            });
        });
    }

    @ParameterizedTest(name = "Content-Type: '{0}' → status {1}")
    @CsvSource({
            "application/x-www-form-urlencoded,           200",
            "application/x-www-form-urlencoded;charset=UTF-8, 200",
            "application/x-www-form-urlencoded; charset=utf-8, 200",
            "application/json,                            415",
            "text/plain,                                  415",
            "text/html,                                   415",
            "application/xml,                             415",
            "APPLICATION/X-WWW-FORM-URLENCODED,           200",
            "Application/X-Www-Form-Urlencoded,           200",
    })
    @Tag("041")
    @DisplayName("Валидация Content-Type заголовка")
    void contentTypeHeaderValidation(String contentType, int expectedStatus) {
        Allure.step("Тестирование Content-Type: " + contentType, () -> {
            Allure.addAttachment("Content-Type", "text/plain", contentType);
            Allure.addAttachment("Ожидаемый статус", "text/plain", String.valueOf(expectedStatus));

            String token = Allure.step("1. Генерация токена", () -> {
                String t = generateToken();
                Allure.addAttachment("Токен", "text/plain", t);
                return t;
            });

            Allure.step("2. Отправка запроса с указанным Content-Type", () -> {
                String expectation = (expectedStatus == 200) ?
                        "Должен быть принят (разрешенный формат)" :
                        "Должен быть отклонен с 415 (неподдерживаемый формат)";

                Allure.addAttachment("Ожидание", "text/plain", expectation);

                given()
                        .header("Content-Type", contentType)
                        .formParam("token", token)
                        .formParam("action", "LOGIN")
                        .when()
                        .post("/endpoint")
                        .then()
                        .statusCode(expectedStatus);

                String result = (expectedStatus == 200) ?
                        "✓ Content-Type принят ✓" :
                        "✓ Content-Type отклонен (415 Unsupported Media Type) ✓";

                Allure.addAttachment("Результат", "text/plain", result);
            });
        });
    }

    @ParameterizedTest(name = "Accept header: {0} → status {1}")
    @CsvSource({
            "application/json, 200",
            "'*/*',            406",
            "text/html,        406",
            "application/xml,  406",
            "text/plain,       406",
            "'',               406",
            "invalid/type,     406"
    })
    @Tag("043")
    @DisplayName("Проверка Accept заголовка")
    void testAcceptHeaderWithCsv(String acceptHeader, int expectedStatus) {
        Allure.step("Тестирование Accept: " + acceptHeader, () -> {
            Allure.addAttachment("Accept header", "text/plain",
                    acceptHeader.isEmpty() ? "[пустая строка]" : acceptHeader);
            Allure.addAttachment("Ожидаемый статус", "text/plain", String.valueOf(expectedStatus));

            String token = Allure.step("1. Генерация токена", () -> {
                String t = com.example.utils.TestDataGenerator.generateValidToken();
                Allure.addAttachment("Токен", "text/plain", t);
                return t;
            });

            Allure.step("2. Отправка запроса с Accept заголовком", () -> {
                String expectation = (expectedStatus == 200) ?
                        "Должен быть принят (поддерживаемый Accept)" :
                        "Должен быть отклонен с 406 (Not Acceptable)";

                Allure.addAttachment("Ожидание", "text/plain", expectation);

                given()
                        .header("Accept", acceptHeader)
                        .formParam("token", token)
                        .formParam("action", "LOGIN")
                        .when()
                        .post("/endpoint")
                        .then()
                        .statusCode(expectedStatus);

                String result = (expectedStatus == 200) ?
                        "✓ Accept заголовок принят ✓" :
                        "✓ Accept заголовок отклонен (406 Not Acceptable) ✓";

                Allure.addAttachment("Результат", "text/plain", result);
            });
        });
    }

    @Test
    @Tag("044")
    @DisplayName("Проверка пустого тела запроса")
    @Severity(NORMAL)
    void emptyRequestBody() {
        Allure.description("Проверка обработки запроса без параметров в теле");

        Allure.step("1. Отправка POST запроса с пустым телом", () -> {
            Allure.addAttachment("Действие", "text/plain",
                    "POST /endpoint с заголовком X-Api-Key но без параметров");
            Allure.addAttachment("Ожидание", "text/plain", "Должен вернуть 400 Bad Request");

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400);

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Пустое тело отклонено\n✓ Статус 400 Bad Request");
        });
    }

    @Test
    @Tag("045")
    @DisplayName("Проверка отсутствия обязательных параметров")
    @Severity(CRITICAL)
    void missingRequiredParameters() {
        Allure.description("Проверка валидации обязательных параметров token и action");

        Allure.step("1. Тест 1: Без параметра token", () -> {
            Allure.addAttachment("Действие", "text/plain", "action=LOGIN без token");
            Allure.addAttachment("Ожидание", "text/plain", "400 Bad Request");

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400);

            Allure.addAttachment("Результат", "text/plain", "✓ token обязателен ✓");
        });

        Allure.step("2. Тест 2: Без параметра action", () -> {
            Allure.addAttachment("Действие", "text/plain", "token=... без action");
            Allure.addAttachment("Ожидание", "text/plain", "400 Bad Request");

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("token", generateToken())
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400);

            Allure.addAttachment("Результат", "text/plain", "✓ action обязателен ✓");
        });

        Allure.step("3. Тест 3: Без обоих параметров", () -> {
            Allure.addAttachment("Действие", "text/plain", "Пустой запрос");
            Allure.addAttachment("Ожидание", "text/plain", "400 Bad Request");

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400);

            Allure.addAttachment("Результат", "text/plain", "✓ Оба параметра обязательны ✓");
        });

        Allure.addAttachment("Итог валидации", "text/plain",
                "✓ Все обязательные параметры проверяются:\n" +
                        "  - token: обязателен ✓\n" +
                        "  - action: обязателен ✓\n" +
                        "  - оба вместе: обязательны ✓");
    }

    @Test
    @Tag("046")
    @DisplayName("Проверка лишних параметров в запросе")
    @Severity(MINOR)
    void extraParametersInRequest() {
        Allure.description("Проверка игнорирования дополнительных параметров в запросе");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с лишними параметрами", () -> {
            Allure.addAttachment("Параметры", "text/plain",
                    "Обязательные: token, action=LOGIN\n" +
                            "Лишние: extra_param=value, another_extra=123");
            Allure.addAttachment("Ожидание", "text/plain",
                    "Лишние параметры должны игнорироваться, запрос должен быть успешным");

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .formParam("extra_param", "value")
                    .formParam("another_extra", "123")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Лишние параметры проигнорированы\n" +
                            "✓ Запрос выполнен успешно\n" +
                            "✓ result: OK");
        });
    }

    @Test
    @Tag("047")
    @DisplayName("Проверка SQL инъекции в параметрах")
    @Severity(CRITICAL)
    void sqlInjectionInParameters() {
        Allure.description("Проверка защиты от SQL инъекций в параметрах запроса");

        String[] sqlInjections = {
                "' OR '1'='1",
                "'; DROP TABLE users; --",
                "' UNION SELECT * FROM users --",
                "admin' --"
        };

        for (String injection : sqlInjections) {
            Allure.step("Тестирование SQL инъекции: " + injection, () -> {
                Allure.addAttachment("Payload", "text/plain", injection);
                Allure.addAttachment("Тип", "text/plain", getSqlInjectionType(injection));

                Allure.step("1. Инъекция в параметре token", () -> {
                    String tokenPayload = injection + "A".repeat(32 - injection.length());
                    Allure.addAttachment("Тестовый токен", "text/plain", tokenPayload);

                    given()
                            .header("X-Api-Key", "qazWSXedc")
                            .formParam("token", tokenPayload)
                            .formParam("action", "LOGIN")
                            .when()
                            .post("/endpoint")
                            .then()
                            .statusCode(400)
                            .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

                    Allure.addAttachment("Результат", "text/plain",
                            "✓ SQL инъекция в token отклонена\n✓ Статус 400");
                });

                Allure.step("2. Инъекция в параметре action", () -> {
                    Allure.addAttachment("Тестовый action", "text/plain", injection);

                    given()
                            .header("X-Api-Key", "qazWSXedc")
                            .formParam("token", generateToken())
                            .formParam("action", injection)
                            .when()
                            .post("/endpoint")
                            .then()
                            .statusCode(400)
                            .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

                    Allure.addAttachment("Результат", "text/plain",
                            "✓ SQL инъекция в action отклонена\n✓ Статус 400");
                });
            });
        }

        Allure.addAttachment("Защита от SQL инъекций", "text/plain",
                "✓ Все типы SQL инъекций отклонены:\n" +
                        "  - Conditional injection (OR '1'='1') ✓\n" +
                        "  - DROP injection ✓\n" +
                        "  - UNION injection ✓\n" +
                        "  - Comment injection (--) ✓");
    }

    @Test
    @Tag("048")
    @DisplayName("Проверка XSS в параметрах")
    @Severity(CRITICAL)
    void xssInParameters() {
        Allure.description("Проверка защиты от XSS атак в параметрах запроса");

        String xssPayload = "<script>alert('xss')</script>";

        Allure.step("1. XSS в параметре token", () -> {
            String tokenPayload = xssPayload + "A".repeat(32 - xssPayload.length());
            Allure.addAttachment("XSS payload", "text/plain", xssPayload);
            Allure.addAttachment("Тестовый токен", "text/plain", tokenPayload);
            Allure.addAttachment("Опасность", "text/plain",
                    "Может выполнить JavaScript при отображении в браузере");

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("token", tokenPayload)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ XSS в token отклонен\n✓ Статус 400 Bad Request");
        });

        Allure.step("2. XSS в параметре action", () -> {
            Allure.addAttachment("XSS payload", "text/plain", xssPayload);
            Allure.addAttachment("Тестовый action", "text/plain", xssPayload);

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("token", generateToken())
                    .formParam("action", xssPayload)
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ XSS в action отклонен\n✓ Статус 400 Bad Request");
        });

        Allure.addAttachment("Защита от XSS", "text/plain",
                "✓ HTML/JavaScript теги не допускаются в параметрах\n" +
                        "✓ XSS payloads отклоняются на уровне валидации\n" +
                        "✓ Защита на уровне входных данных");
    }

    @Test
    @Tag("049")
    @DisplayName("Проверка path traversal")
    @Severity(CRITICAL)
    void pathTraversal() {
        Allure.description("Проверка защиты от атак path traversal");

        String[] paths = {
                "../endpoint",
                "....//endpoint",
                "/endpoint/../admin",
                "/endpoint%00",
                "/endpoint\0"
        };

        for (String path : paths) {
            Allure.step("Тестирование path: " + path, () -> {
                Allure.addAttachment("Путь", "text/plain", path);
                Allure.addAttachment("Тип атаки", "text/plain", getPathTraversalType(path));
                Allure.addAttachment("Ожидание", "text/plain", "400 Bad Request или 404 Not Found");

                given()
                        .header("X-Api-Key", "qazWSXedc")
                        .formParam("token", generateToken())
                        .formParam("action", "LOGIN")
                        .when()
                        .post(path)
                        .then()
                        .statusCode(400);

                Allure.addAttachment("Результат", "text/plain", "✓ Path traversal отклонен");
            });
        }

        Allure.addAttachment("Защита от path traversal", "text/plain",
                "✓ Все типы path traversal отклонены:\n" +
                        "  - ../ атаки ✓\n" +
                        "  - Двойные слеши ✓\n" +
                        "  - Null-byte injection ✓\n" +
                        "  - URL encoding обходы ✓");
    }

    @Test
    @Tag("050")
    @DisplayName("Проверка CSRF уязвимости")
    @Severity(NORMAL)
    void csrfProtection() {
        Allure.description("Проверка защиты от Cross-Site Request Forgery");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Тест 1: Запрос с Origin от другого домена", () -> {
            Allure.addAttachment("Заголовок", "text/plain", "Origin: http://evil.com");
            Allure.addAttachment("Ожидание", "text/plain",
                    "REST API обычно не защищены от CSRF, должен быть 200");

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .header("Origin", "http://evil.com")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200);

            Allure.addAttachment("Результат", "text/plain",
                    "✓ REST API без CSRF защиты (ожидаемо)\n✓ Статус 200");
        });

        Allure.step("3. Тест 2: Запрос с Referer от другого домена", () -> {
            Allure.addAttachment("Заголовок", "text/plain", "Referer: http://evil.com");
            Allure.addAttachment("Ожидание", "text/plain", "Должен вернуть 403 Forbidden");

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .header("Referer", "http://evil.com")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(403);

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Referer проверяется\n✓ Статус 403 Forbidden");
        });

        Allure.addAttachment("CSRF защита", "text/plain",
                "✓ API частично защищено от CSRF:\n" +
                        "  - Origin: не проверяется (стандартно для REST API) ✓\n" +
                        "  - Referer: проверяется, блокирует внешние домены ✓");
    }

    @Test
    @Tag("051")
    @DisplayName("Проверка rate limiting")
    @Severity(NORMAL)
    void rateLimiting() {
        Allure.description("Проверка ограничения частоты запросов (rate limiting)");

        Allure.step("1. Отправка 50 запросов подряд", () -> {
            Allure.addAttachment("Количество запросов", "text/plain", "50");
            Allure.addAttachment("Цель", "text/plain",
                    "Проверка срабатывания rate limiting при большом количестве запросов");

            int successfulRequests = 0;
            int rateLimitedRequests = 0;

            for (int i = 0; i < 50; i++) {
                int status = given()
                        .header("X-Api-Key", "qazWSXedc")
                        .formParam("token", generateToken())
                        .formParam("action", "LOGIN")
                        .when()
                        .post("/endpoint")
                        .then()
                        .extract().statusCode();

                if (status == 200) successfulRequests++;
                if (status == 429) rateLimitedRequests++;
            }

            Allure.addAttachment("Статистика", "text/plain",
                    "Успешных запросов: " + successfulRequests + "\n" +
                            "Ограниченных запросов (429): " + rateLimitedRequests + "\n" +
                            "Всего: 50");

            Allure.addAttachment("Результат", "text/plain",
                    (rateLimitedRequests > 0) ?
                            "✓ Rate limiting работает ✓" :
                            "✗ Rate limiting не сработал (все 200 OK)");
        });
    }

    // Вспомогательные методы
    private String getSqlInjectionType(String payload) {
        if (payload.contains("DROP")) return "DROP injection";
        if (payload.contains("UNION")) return "UNION injection";
        if (payload.contains("OR '1'='1")) return "Conditional injection";
        if (payload.contains("--")) return "Comment injection";
        return "SQL injection";
    }

    private String getPathTraversalType(String path) {
        if (path.contains("../")) return "Directory traversal";
        if (path.contains("....//")) return "Double dot slash";
        if (path.contains("%00")) return "Null byte injection";
        if (path.contains("\0")) return "Null character";
        return "Path manipulation";
    }
}