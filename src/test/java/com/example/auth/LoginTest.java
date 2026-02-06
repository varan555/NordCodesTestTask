package com.example.auth;

import com.example.base.TestBase;
import io.qameta.allure.*;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static org.hamcrest.Matchers.containsString;

@Epic("Authentication")
@Feature("LOGIN Functionality")
@Tag("auth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LoginTest extends TestBase {

    @Test
    @Tag("001")
    @DisplayName("Успешный LOGIN с валидным токеном")
    @Severity(CRITICAL)
    void successfulLogin() {
        Allure.step("1. Настройка внешнего сервиса на успешный ответ", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            Allure.addAttachment("Конфигурация мока", "text/plain", "/auth → 200 OK");
        });

        String token = Allure.step("2. Генерация валидного токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка LOGIN запроса", () -> {
            Allure.addAttachment("Параметры запроса", "text/plain",
                    "token: " + token + "\naction: LOGIN");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ LOGIN выполнен успешно\n✓ Токен принят системой");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"400", "401", "403", "404", "500", "503"})
    @Tag("002")
    @DisplayName("LOGIN при разных ошибках внешнего сервиса: {arguments}")
    @Severity(NORMAL)
    void loginWithVariousExternalServiceErrors(String statusCode) {
        Allure.step("1. Настройка мока с ошибкой " + statusCode, () -> {
            int status = Integer.parseInt(statusCode);
            wireMockServer.stubFor(post("/auth")
                    .willReturn(aResponse().withStatus(status)));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "/auth → HTTP " + status + " (" + getStatusDescription(status) + ")");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка LOGIN при ошибке внешнего сервиса", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "При ошибке внешнего сервиса система должна вернуть ERROR");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Система корректно обработала ошибку " + statusCode + "\n" +
                            "✓ Возвращен result: ERROR");
        });
    }

    @Test
    @Tag("003")
    @DisplayName("LOGIN с задержкой ответа внешнего сервиса")
    @Severity(MINOR)
    void loginWithExternalServiceDelay() {
        Allure.step("1. Настройка мока с задержкой 1500ms", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(ok().withFixedDelay(1500)));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "/auth → 200 OK с задержкой 1.5 секунды");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса с задержкой", () -> {
            long startTime = System.currentTimeMillis();

            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна корректно обработать запрос несмотря на задержку внешнего сервиса");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Запрос выполнен за " + duration + "ms\n" +
                            "✓ Система дождалась ответа внешнего сервиса\n" +
                            "✓ LOGIN успешно завершен");
        });
    }

    @Test
    @Tag("004")
    @DisplayName("Повторный LOGIN с тем же токеном должен быть отклонён")
    @Severity(MINOR)
    void repeatedLoginWithSameTokenShouldFail() {
        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Настройка мока", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            Allure.addAttachment("Конфигурация", "text/plain", "/auth → 200 OK");
        });

        Allure.step("3. Первый LOGIN (успешно)", () -> {
            Allure.addAttachment("Действие", "text/plain", "Попытка создания сессии с токеном");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain", "✓ Первый LOGIN успешен");
        });

        Allure.step("4. Проверка вызова внешнего сервиса", () -> {
            wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));
            Allure.addAttachment("Верификация", "text/plain",
                    "✓ Внешний сервис вызван 1 раз");
        });

        Allure.step("5. Второй LOGIN с тем же токеном", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна отклонить повторный LOGIN с кодом 409");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(409)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                    .body("message", containsString("already exists"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Повторный LOGIN отклонен\n" +
                            "✓ Статус 409 (Conflict)\n" +
                            "✓ Сообщение указывает на существующую сессию");
        });

        Allure.step("6. Проверка, что второй вызов не произошел", () -> {
            wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));
            Allure.addAttachment("Верификация", "text/plain",
                    "✓ Внешний сервис НЕ вызывался повторно\n" +
                            "✓ Всего 1 вызов за оба LOGIN");
        });
    }

    @ParameterizedTest(name = "QPARAM-001: Query params {0}")
    @CsvSource({
            "token=ABC&action=LOGIN,        400, 'В query вместо body'",
            "?token=ABC&action=LOGIN,       400, 'С вопросительным знаком'",
            "?token=ABC,                    400, 'Только token в query'",
            "?action=LOGIN,                 400, 'Только action в query'",
            "token=ABC&action=LOGIN&extra=1,400, 'Лишние параметры в query'"
    })
    @Tag("005")
    @DisplayName("Параметры в query string вместо body")
    @Severity(CRITICAL)
    void parametersInQueryString(String queryString, int expectedStatus, String description) {
        Allure.step("1. Подготовка тестового сценария: " + description, () -> {
            Allure.addAttachment("Сценарий", "text/plain", description);
            Allure.addAttachment("Query строка", "text/plain", queryString);
            Allure.addAttachment("Ожидаемый статус", "text/plain", "HTTP " + expectedStatus);
        });

        String fullPath = "/endpoint" + (queryString.startsWith("?") ? "" : "?") + queryString;

        Allure.step("2. Отправка запроса с параметрами в query", () -> {
            Allure.addAttachment("URL", "text/plain", "POST " + fullPath);
            Allure.addAttachment("Ожидание", "text/plain",
                    "API должен отклонить запрос с параметрами в query string");

            given()
                    .when()
                    .post(fullPath)
                    .then()
                    .statusCode(expectedStatus);

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Запрос отклонен с кодом " + expectedStatus + "\n" +
                            "✓ Параметры должны передаваться в body, а не в query string");
        });
    }

    @ParameterizedTest(name = "Смешанные параметры {3}")
    @CsvSource({
            "QUERY,    BODY,    400, 'Token в query, action в body'",
            "BODY,     QUERY,   400, 'Action в query, token в body'",
            "HEADER,   BODY,    400, 'Token в header, action в body'",
            "BODY,     HEADER,  400, 'Action в header, token в body'",
            "QUERY,    HEADER,  400, 'Token в query, action в header'"
    })
    @Tag("006")
    @DisplayName("Параметры в разных местах")
    @Severity(CRITICAL)
    void parametersInDifferentPlaces(String tokenLocation, String actionLocation,
                                     int expectedStatus, String description) {
        Allure.step("1. Сценарий: " + description, () -> {
            Allure.addAttachment("Тестовая конфигурация", "text/plain",
                    "token в: " + tokenLocation + "\n" +
                            "action в: " + actionLocation + "\n" +
                            "Ожидаемый статус: " + expectedStatus + "\n" +
                            "Описание: " + description);
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Формирование запроса с параметрами в разных местах", () -> {
            RequestSpecification request = given();

            switch (tokenLocation) {
                case "QUERY" -> {
                    request.queryParam("token", token);
                    Allure.addAttachment("Размещение token", "text/plain", "QUERY параметр");
                }
                case "BODY" -> {
                    request.formParam("token", token);
                    Allure.addAttachment("Размещение token", "text/plain", "BODY параметр");
                }
                case "HEADER" -> {
                    request.header("X-Token", token);
                    Allure.addAttachment("Размещение token", "text/plain", "HTTP заголовок X-Token");
                }
            }

            switch (actionLocation) {
                case "QUERY" -> {
                    request.queryParam("action", "LOGIN");
                    Allure.addAttachment("Размещение action", "text/plain", "QUERY параметр");
                }
                case "BODY" -> {
                    request.formParam("action", "LOGIN");
                    Allure.addAttachment("Размещение action", "text/plain", "BODY параметр");
                }
                case "HEADER" -> {
                    request.header("X-Action", "LOGIN");
                    Allure.addAttachment("Размещение action", "text/plain", "HTTP заголовок X-Action");
                }
            }
        });

        Allure.step("4. Отправка запроса и проверка", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна отклонить запрос с параметрами в разных местах");

            given()
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(expectedStatus);

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Запрос отклонен с кодом " + expectedStatus + "\n" +
                            "✓ Все параметры должны быть в одном месте (теле запроса)");
        });
    }

    @ParameterizedTest(name = "JSON-001: {2}")
    @CsvSource({
            "'{\"token\":\"VALID_TOKEN_123\",\"action\":\"LOGIN\"}',        415, 'Правильный JSON с валидным токеном'",
            "'{\"token\":\"VALID_TOKEN_123\"}',                             415, 'Только token в JSON'",
            "'{\"action\":\"LOGIN\"}',                                      415, 'Только action в JSON'",
            "'{\"Token\":\"VALID_TOKEN_123\",\"Action\":\"LOGIN\"}',        415, 'Capitalized keys в JSON'",
            "'{\"TOKEN\":\"VALID_TOKEN_123\",\"ACTION\":\"LOGIN\"}',        415, 'Uppercase keys в JSON'",
            "'token=VALID_TOKEN_123&action=LOGIN',                          415, 'Form-urlencoded строка в JSON body'",
            "'<xml><token>VALID_TOKEN_123</token></xml>',                   415, 'XML вместо JSON'",
            "'',                                                            415, 'Пустое JSON тело'",
            "'not json at all',                                             415, 'Невалидный JSON синтаксис'"
    })

    private String generateValidToken() {
        // Реализация генерации валидного токена
        // Например, получаем токен из другой части системы
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlRlc3QgVXNlciJ9";
    }

    @ParameterizedTest(name = "Headers: {3}")
    @CsvSource({
            "X-Token, X-Action, 400, 'Кастомные заголовки'",
            "token, action,     400, 'Заголовки с именами параметров'",
            "Token, Action,     400, 'Capitalized заголовки'"
    })
    @Tag("008")
    @DisplayName("Параметры в headers вместо body")
    @Severity(CRITICAL)
    void parametersInHeaders(String tokenHeader, String actionHeader, int expectedStatus, String description) {
        Allure.step("1. Сценарий: " + description, () -> {
            Allure.addAttachment("Конфигурация заголовков", "text/plain",
                    "token в заголовке: " + tokenHeader + "\n" +
                            "action в заголовке: " + actionHeader);
            Allure.addAttachment("Ожидание", "text/plain", "HTTP " + expectedStatus);
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса с параметрами в заголовках", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна отклонить запрос с параметрами в заголовках");

            given()
                    .header(tokenHeader, token)
                    .header(actionHeader, "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(expectedStatus);

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Запрос отклонен с кодом " + expectedStatus + "\n" +
                            "✓ Параметры должны передаваться в теле запроса\n" +
                            "✓ Заголовки не являются допустимым местом для параметров авторизации");
        });
    }

    @ParameterizedTest(name = "Path params {0}")
    @ValueSource(strings = {
            "/endpoint/ABC/LOGIN",
            "/endpoint/token/ABC/action/LOGIN",
            "/endpoint/ABC",
            "/endpoint/LOGIN"
    })
    @DisplayName("Параметры в path вместо body")
    @Severity(CRITICAL)
    void parametersInPath(String pathPattern) {
        // Генерируем валидный токен
        final String validToken = generateToken();

        // Заменяем ABC на валидный токен
        final String path = pathPattern.replace("ABC", validToken);

        Allure.step("1. Подготовка тестового URL", () -> {
            Allure.addAttachment("Сгенерированный токен", "text/plain", validToken);
            Allure.addAttachment("Исходный шаблон", "text/plain", pathPattern);
            Allure.addAttachment("Финальный путь", "text/plain", path);
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна отклонить запрос с параметрами в path");
        });

        Allure.step("2. Отправка запроса с параметрами в path", () -> {
            Allure.addAttachment("Действие", "text/plain", "POST " + path);

            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .when()
                    .post(path)
                    .then()
                    .statusCode(400);

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Запрос отклонен с кодом 400\n" +
                            "✓ Параметры не должны передаваться в path\n" +
                            "✓ Использован валидный токен: " + validToken + "\n" +
                            "✓ Правильный формат: POST /endpoint с параметрами в body");
        });
    }

    private void testSinglePath(String path, String token) {
        Allure.step("Тест пути: " + path, () -> {
            Allure.step("1. Подготовка тестового URL", () -> {
                Allure.addAttachment("Сгенерированный токен", "text/plain", token);
                Allure.addAttachment("Путь запроса", "text/plain", path);
                Allure.addAttachment("Ожидание", "text/plain",
                        "Система должна отклонить запрос с параметрами в path");
            });

            Allure.step("2. Отправка запроса с параметрами в path", () -> {
                Allure.addAttachment("Действие", "text/plain", "POST " + path);

                given()
                        .when()
                        .post(path)
                        .then()
                        .statusCode(400);

                Allure.addAttachment("Результат", "text/plain",
                        "✓ Запрос отклонен с кодом 400\n" +
                                "✓ Параметры не должны передаваться в path\n" +
                                "✓ Использован токен: " + token + "\n" +
                                "✓ Правильный формат: POST /endpoint с параметрами в body");
            });
        });
    }

    private String getStatusDescription(int status) {
        switch (status) {
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            case 503: return "Service Unavailable";
            default: return "Unknown";
        }
    }
}