package com.example.security;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;

@Epic("Security")
@Feature("API Key Authentication")
@Tag("security")
@Tag("authentication")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ApiKeyTest extends TestBase {

    @BeforeAll
    void setupWireMock() {
        Allure.step("Настройка WireMock для всех тестов", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            Allure.addAttachment("Конфигурация", "text/plain", "/auth → 200 OK");
        });
    }

    @Test
    @Tag("052")
    @DisplayName("Успешный запрос с валидным API ключом")
    @Severity(CRITICAL)
    void validApiKey() {
        Allure.description("Проверка базовой авторизации с корректным API ключом");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с валидным API ключом", () -> {
            Allure.addAttachment("API ключ", "text/plain", "qazWSXedc (валидный)");
            Allure.addAttachment("Ожидание", "text/plain", "200 OK - успешная авторизация");

            givenWithoutApiKey()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ API ключ принят\n✓ Статус 200 OK\n✓ result: OK");
        });
    }

    @Test
    @Tag("053")
    @DisplayName("Запрос без API ключа")
    @Severity(CRITICAL)
    void missingApiKey() {
        Allure.description("Проверка обработки запроса без заголовка X-Api-Key");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса без API ключа", () -> {
            Allure.addAttachment("Отсутствует", "text/plain", "Заголовок X-Api-Key");
            Allure.addAttachment("Ожидание", "text/plain", "401 Unauthorized");

            givenWithoutApiKey()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(401)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Без API ключа доступ запрещен\n✓ Статус 401 Unauthorized");
        });
    }

    @Test
    @Tag("054")
    @DisplayName("Неверный API ключ")
    @Severity(CRITICAL)
    void invalidApiKey() {
        Allure.description("Проверка обработки некорректного API ключа");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с неверным API ключом", () -> {
            Allure.addAttachment("API ключ", "text/plain", "WRONG_KEY (неверный)");
            Allure.addAttachment("Ожидание", "text/plain", "401 Unauthorized");

            givenWithoutApiKey()
                    .header("X-Api-Key", "WRONG_KEY")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(401)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Неверный API ключ отклонен\n✓ Статус 401 Unauthorized");
        });
    }

    @Test
    @Tag("055")
    @DisplayName("Пустой API ключ")
    @Severity(NORMAL)
    void emptyApiKey() {
        Allure.description("Проверка обработки пустого API ключа");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с пустым API ключом", () -> {
            Allure.addAttachment("API ключ", "text/plain", "[пустая строка]");
            Allure.addAttachment("Ожидание", "text/plain", "401 Unauthorized");

            givenWithoutApiKey()
                    .header("X-Api-Key", "")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(401)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Пустой API ключ отклонен\n✓ Статус 401 Unauthorized");
        });
    }

    @Test
    @Tag("056")
    @DisplayName("API ключ с пробелами")
    @Severity(NORMAL)
    void apiKeyWithSpaces() {
        Allure.description("Проверка обработки API ключа с пробелами по краям");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с API ключом с пробелами", () -> {
            Allure.addAttachment("API ключ", "text/plain", "' qazWSXedc ' (с пробелами)");
            Allure.addAttachment("Ожидание", "text/plain", "403 Forbidden");

            givenWithoutApiKey()
                    .header("X-Api-Key", " qazWSXedc ")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(403)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ API ключ с пробелами отклонен\n✓ Статус 403 Forbidden");
        });
    }

    @ParameterizedTest(name = "Неверное имя: '{0}' → ожидаем {1}")
    @CsvSource({
            "X-API-Ke,      401",      // опечатка
            "API-Key,       401",       // без X-
            "XApiKey,       401",       // без дефисов
            "X-API-KEYS,    401",       // множественное число
            "X_API_KEY,     401",       // подчеркивания
            "x-api key,     400",       // пробел вместо дефиса
            "X-API-Key:,    401",       // с двоеточием
            "'',            400",       // пустое имя заголовка
            "' ',           400",       // пробел как имя
            "X--API-Key,    401",       // двойной дефис
            "-X-API-Key,    401",       // дефис в начале
            "X-API-Key-,    401",       // дефис в конце
    })
    @Tag("057")
    @DisplayName("Неверное имя заголовка API ключа")
    @Severity(MINOR)
    void invalidApiKeyHeaderNames(String headerName, int expectedStatus) {
        Allure.step("1. Подготовка тестового сценария", () -> {
            Allure.addAttachment("Тестируемый заголовок", "text/plain", headerName.isEmpty() ? "(пустая строка)" : headerName);
            Allure.addAttachment("Правильное имя заголовка", "text/plain", "X-API-Key");
            Allure.addAttachment("Ожидаемый статус", "text/plain", "HTTP " + expectedStatus);

            String errorType = expectedStatus == 400 ? "400 Bad Request" : "401 Unauthorized";
            Allure.addAttachment("Тип ошибки", "text/plain", errorType);

            Allure.addAttachment("Особый случай", "text/plain",
                    headerName.equals("X_API_KEY") ?
                            "⚠️ Особый случай: возвращает 400 вместо 401\n" +
                                    "   Возможная причина: сервер обрабатывает подчеркивания как невалидный синтаксис заголовка" :
                            "Стандартная обработка неверного имени заголовка");
        });

        String token = Allure.step("2. Генерация валидного токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Сгенерированный токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса с неверным именем заголовка", () -> {
            Allure.addAttachment("Детали запроса", "text/plain",
                    "Заголовок: \"" + headerName + "\" = qazWSXedc\n" +
                            "Токен: " + token + "\n" +
                            "Action: LOGIN\n" +
                            "Content-Type: application/x-www-form-urlencoded");

            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна вернуть " + expectedStatus + " для заголовка \"" + headerName + "\"\n" +
                            (expectedStatus == 400 ?
                                    "400 Bad Request - невалидный синтаксис имени заголовка" :
                                    "401 Unauthorized - заголовок не распознан как X-API-Key"));

            givenWithoutApiKey()
                    .header(headerName, "qazWSXedc")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(expectedStatus);

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Запрос отклонен с кодом " + expectedStatus + "\n" +
                            "✓ Заголовок \"" + (headerName.isEmpty() ? "(пустой)" : headerName) + "\" обработан корректно\n" +
                            (headerName.equals("X_API_KEY") ?
                                    "✓ Особый случай: 400 для подчеркиваний (синтаксическая ошибка)\n" :
                                    "✓ Стандартная обработка: " + expectedStatus + " для неверного имени\n") +
                            "✓ API правильно различает разные типы ошибок имен заголовков");
        });

        Allure.step("4. Анализ различий в кодах ошибок", () -> {
            if (headerName.equals("X_API_KEY")) {
                Allure.addAttachment("Анализ кода 400 для X_API_KEY", "text/plain",
                        "Почему 400, а не 401:\n\n" +
                                "1. **Синтаксическая ошибка**: Имя заголовка с подчеркиваниями может\n" +
                                "   рассматриваться как невалидный синтаксис HTTP заголовка\n\n" +
                                "2. **Стандарт HTTP**: RFC 7230 определяет, что имена заголовков\n" +
                                "   должны состоять из токенов, где подчеркивание не является\n" +
                                "   стандартным символом (дефисы разрешены)\n\n" +
                                "3. **Различие в обработке**:\n" +
                                "   • 400 Bad Request: 'не могу разобрать этот заголовок'\n" +
                                "   • 401 Unauthorized: 'не нашел обязательный заголовок X-API-Key'\n\n" +
                                "4. **Безопасность**: Раннее отклонение невалидного синтаксиса\n" +
                                "   предотвращает потенциальные атаки с нестандартными заголовками");
            } else {
                Allure.addAttachment("Анализ кода " + expectedStatus, "text/plain",
                        expectedStatus == 400 ?
                                "400 Bad Request - синтаксическая ошибка в имени заголовка" :
                                "401 Unauthorized - заголовок не найден или не распознан");
            }
        });
    }



    @Test
    @Tag("058")
    @DisplayName("Несколько заголовков X-Api-Key")
    @Severity(MINOR)
    void multipleApiKeyHeaders() {
        Allure.description("Проверка обработки запроса с несколькими заголовками X-Api-Key");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с дублирующимися заголовками", () -> {
            Allure.addAttachment("Заголовки", "text/plain",
                    "X-Api-Key: qazWSXedc\nX-Api-Key: ANOTHER_KEY");
            Allure.addAttachment("Ожидание", "text/plain",
                    "403 Forbidden - дублирующие заголовки должны отклоняться");

            givenWithoutApiKey()
                    .header("X-Api-Key", "qazWSXedc")
                    .header("X-Api-Key", "ANOTHER_KEY")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(403)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Дублирующие заголовки отклонены\n✓ Статус 403 Forbidden");
        });
    }

    @ParameterizedTest(name = "Валидный регистр: {0}")
    @ValueSource(strings = {
            "X-API-Key", "x-api-key", "X-API-KEY", "X-Api-Key", "x-Api-key"
    })
    @DisplayName("Регистронезависимость заголовка API ключа")
    @Tag("007")
    @Severity(CRITICAL)
    void apiKeyHeaderCaseVariations(String headerName) {
        // ОЖИДАЕМ: 200 OK
        givenWithoutApiKey()
                .header(headerName, "qazWSXedc")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200);
    }

    @Test
    @Tag("059")
    @DisplayName("API ключ с SQL инъекцией")
    @Severity(CRITICAL)
    void apiKeyWithSqlInjection() {
        Allure.description("Проверка защиты от SQL инъекций в API ключе");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с SQL инъекцией в API ключе", () -> {
            Allure.addAttachment("API ключ", "text/plain", "' OR '1'='1");
            Allure.addAttachment("Тип атаки", "text/plain", "SQL conditional injection");
            Allure.addAttachment("Ожидание", "text/plain", "401 Unauthorized");

            givenWithoutApiKey()
                    .header("X-Api-Key", "' OR '1'='1")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(401)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ SQL инъекция в API ключе отклонена\n✓ Статус 401 Unauthorized");
        });
    }

    @Test
    @Tag("060")
    @DisplayName("API ключ с XSS payload")
    @Severity(CRITICAL)
    void apiKeyWithXssPayload() {
        Allure.description("Проверка защиты от XSS атак в API ключе");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с XSS в API ключе", () -> {
            Allure.addAttachment("API ключ", "text/plain", "<script>alert('xss')</script>");
            Allure.addAttachment("Тип атаки", "text/plain", "Cross-Site Scripting");
            Allure.addAttachment("Опасность", "text/plain",
                    "Может выполнить JavaScript при отображении в логах/админке");
            Allure.addAttachment("Ожидание", "text/plain", "401 Unauthorized");

            givenWithoutApiKey()
                    .header("X-Api-Key", "<script>alert('xss')</script>")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(401)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ XSS в API ключе отклонен\n✓ Статус 401 Unauthorized");
        });
    }

    @Test
    @Tag("061")
    @DisplayName("Очень длинный API ключ")
    @Severity(MINOR)
    void veryLongApiKey() {
        Allure.description("Проверка обработки очень длинного API ключа (10KB)");

        String longApiKey = "A".repeat(10000);

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с 10KB API ключом", () -> {
            Allure.addAttachment("Длина API ключа", "text/plain", "10 000 символов (~10KB)");
            Allure.addAttachment("Ожидание", "text/plain", "400 Bad Request");

            givenWithoutApiKey()
                    .header("X-Api-Key", longApiKey)
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Слишком длинный API ключ отклонен\n✓ Статус 400 Bad Request");
        });
    }

    @Test
    @Tag("062")
    @DisplayName("API ключ с null character")
    @Severity(CRITICAL)
    void apiKeyWithNullCharacter() {
        Allure.description("Проверка обработки API ключа с null character (\\0)");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с null byte в API ключе", () -> {
            Allure.addAttachment("API ключ", "text/plain", "qazWSX\\0edc (с null byte)");
            Allure.addAttachment("Тип атаки", "text/plain", "Null byte injection");
            Allure.addAttachment("Опасность", "text/plain",
                    "Может обойти проверки строк в некоторых языках");
            Allure.addAttachment("Ожидание", "text/plain", "401 Unauthorized");

            givenWithoutApiKey()
                    .header("X-Api-Key", "qazWSX\0edc")
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(401)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ API ключ с null byte отклонен\n✓ Статус 401 Unauthorized");
        });
    }

    @Test
    @Tag("063")
    @DisplayName("API ключ с бинарными данными")
    @Severity(CRITICAL)
    void apiKeyWithBinaryData() {
        Allure.description("Проверка обработки API ключа с бинарными данными");

        byte[] binaryData = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
        String binaryApiKey = new String(binaryData);

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Отправка запроса с бинарным API ключом", () -> {
            Allure.addAttachment("API ключ", "text/plain", "[бинарные данные 0x00-0x04]");
            Allure.addAttachment("Тип данных", "text/plain", "Binary/non-printable characters");
            Allure.addAttachment("Ожидание", "text/plain", "401 Unauthorized");

            givenWithoutApiKey()
                    .header("X-Api-Key", binaryApiKey)
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(401)
                    .body(org.hamcrest.Matchers.anything());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Бинарный API ключ отклонен\n✓ Статус 401 Unauthorized");
        });
    }

    @ParameterizedTest(name = "API ключ: {2} → status {1}")
    @CsvSource({
            "qazWSXedc,           200, 'Оригинальный ключ'",
            "'qazWSXedc\\u00E9',  401, 'Latin-1 символ é'",
            "'qazWSXedc\\u4E2D',  401, 'Unicode символ 中'",
            "'qazWSXedc%20',      401, 'URL encoded пробел'",
            "'qazWSXedc+',        401, 'Plus sign'",
            "'qazWSXedc\\0',      401, 'Null byte'",
            "'qazWSXedc\\n',      401, 'Newline'",
            "'qazWSXedc\\t',      401, 'Tab'",
            "'QAZWSXEDC',         401, 'Верхний регистр'",
            "'qazwsxedc',         401, 'Нижний регистр'",
            "'QazWsxEdc',         401, 'Смешанный регистр'",
            "' qazWSXedc',        401, 'Пробел в начале'",
            "'qazWSXedc ',        401, 'Пробел в конце'",
            "'qazWSXedc  ',       401, 'Два пробела в конце'",
            "'',                  401, 'Пустая строка'",
            "'   ',               401, 'Только пробелы'",
            "'qazWSXedc'.repeat(10), 401, 'Очень длинный ключ'"
    })
    @Tag("064")
    @DisplayName("API ключ с разными кодировками и регистром")
    void apiKeyEncodingsAndCase(String apiKey, int expectedStatus, String description) {
        Allure.step("Тестирование API ключа: " + description, () -> {
            Allure.addAttachment("Описание", "text/plain", description);
            Allure.addAttachment("API ключ", "text/plain", apiKey);
            Allure.addAttachment("Ожидаемый статус", "text/plain", String.valueOf(expectedStatus));

            String token = Allure.step("1. Генерация токена", () -> {
                String t = generateToken();
                Allure.addAttachment("Токен", "text/plain", t);
                return t;
            });

            Allure.step("2. Отправка запроса", () -> {
                givenWithoutApiKey()
                        .header("X-Api-Key", unescapeString(apiKey))
                        .formParam("token", token)
                        .formParam("action", "LOGIN")
                        .when()
                        .post("/endpoint")
                        .then()
                        .statusCode(expectedStatus)
                        .log().ifValidationFails();

                String result = (expectedStatus == 200) ?
                        "✓ API ключ принят (ожидаемо)" :
                        "✓ API ключ отклонен (ожидаемо)";

                Allure.addAttachment("Результат", "text/plain", result + "\n✓ Статус: " + expectedStatus);
            });
        });
    }

    private String unescapeString(String str) {
        return str.replace("\\u00E9", "é")
                .replace("\\u4E2D", "中")
                .replace("\\0", "\0")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
    }
}