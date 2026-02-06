package com.example.integration;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;

@Epic("Integration")
@Feature("Error Handling")
@Tag("integration")
@Tag("error-handling")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ErrorHandlingTest extends TestBase {

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    @Tag("023")
    @DisplayName("Обработка сетевой ошибки при подключении к внешнему сервису")
    @Severity(CRITICAL)
    void networkErrorWhenConnectingToExternalService() {
        Allure.description("Эмуляция полной недоступности внешнего сервиса авторизации");

        Allure.step("1. Остановка WireMock для эмуляции сетевой ошибки", () -> {
            wireMockServer.stop();
            Allure.addAttachment("Состояние", "text/plain", "WireMock остановлен (сервис недоступен)");
            Allure.addAttachment("Цель", "text/plain", "Эмуляция: Connection refused / Network unreachable");
        });

        try {
            Allure.step("2. Попытка LOGIN при недоступном внешнем сервисе", () -> {
                String token = generateToken();
                Allure.addAttachment("Токен", "text/plain", token);

                Allure.addAttachment("Ожидание", "text/plain",
                        "Система должна вернуть 500 Internal Server Error");

                given()
                        .formParam("token", token)
                        .formParam("action", "LOGIN")
                        .when()
                        .post("/endpoint")
                        .then()
                        .statusCode(500)
                        .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                        .body("message", org.hamcrest.Matchers.notNullValue());

                Allure.addAttachment("Результат", "text/plain",
                        "✓ Система корректно обработала сетевую ошибку\n" +
                                "✓ Возвращен статус 500\n" +
                                "✓ Сообщение об ошибке присутствует");
            });
        } finally {
            Allure.step("3. Восстановление WireMock", () -> {
                wireMockServer.start();
                Allure.addAttachment("Состояние", "text/plain", "WireMock перезапущен");
            });
        }
    }

    @Test
    @Tag("024")
    @DisplayName("Обработка таймаута подключения к внешнему сервису")
    @Severity(CRITICAL)
    void connectionTimeoutToExternalService() {
        Allure.description("Эмуляция превышения времени ожидания подключения к внешнему сервису");

        Allure.step("1. Настройка мока с задержкой 30 секунд", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(ok().withFixedDelay(30000)));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "Задержка ответа: 30000ms (30 секунд)\n" +
                            "Ожидание: превышение connection timeout");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса с ожиданием таймаута", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна вернуть 500 после превышения timeout подключения");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(500)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                    .body("message", org.hamcrest.Matchers.notNullValue());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Таймаут подключения обработан корректно\n" +
                            "✓ Статус 500 Internal Server Error\n" +
                            "✓ Система не зависает на вечном ожидании");
        });
    }

    @Test
    @Tag("025")
    @DisplayName("Обработка таймаута чтения от внешнего сервису")
    @Severity(NORMAL)
    void readTimeoutFromExternalService() {
        Allure.description("Эмуляция медленного ответа от внешнего сервиса (read timeout)");

        Allure.step("1. Настройка chunked ответа с задержкой", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(ok()
                            .withChunkedDribbleDelay(10, 10000)));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "Chunked ответ: 10 чанков за 10000ms\n" +
                            "Эмуляция: медленная передача данных");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса с медленным ответом", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна дождаться ответа (если read timeout достаточно большой)");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Медленный ответ обработан успешно\n" +
                            "✓ Read timeout не превышен\n" +
                            "✓ LOGIN выполнен");
        });
    }

    @Test
    @Tag("026")
    @DisplayName("Внешний сервис закрывает соединение без ответа")
    @Severity(NORMAL)
    void externalServiceClosesConnectionWithoutResponse() {
        Allure.description("Эмуляция сброса соединения внешним сервисом (connection reset)");

        Allure.step("1. Настройка мока с fault CONNECTION_RESET_BY_PEER", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(aResponse()
                            .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));
            Allure.addAttachment("Тип ошибки", "text/plain", "CONNECTION_RESET_BY_PEER");
            Allure.addAttachment("Эмуляция", "text/plain", "Сервис закрывает соединение без отправки ответа");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса при сбросе соединения", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна вернуть 500 при сбросе соединения");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(500)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Сброс соединения обработан корректно\n" +
                            "✓ Статус 500 Internal Server Error");
        });
    }

    @Test
    @Tag("027")
    @DisplayName("Внешний сервис возвращает пустой ответ")
    @Severity(MINOR)
    void externalServiceReturnsEmptyResponse() {
        Allure.description("Проверка обработки пустого ответа от внешнего сервиса");

        Allure.step("1. Настройка мока с пустым телом", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody("")));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "Статус: 200 OK\nТело: [пустое]");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса при пустом ответе", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Пустое тело при статусе 200 должно считаться успешным ответом");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Пустой ответ обработан корректно\n" +
                            "✓ Главное - статус 200, тело может быть пустым\n" +
                            "✓ LOGIN успешен");
        });
    }

    @Test
    @Tag("028")
    @DisplayName("Внешний сервис возвращает невалидный JSON")
    @Severity(MINOR)
    void externalServiceReturnsInvalidJson() {
        Allure.description("Проверка обработки некорректного JSON от внешнего сервиса");

        Allure.step("1. Настройка мока с невалидным JSON", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(ok()
                            .withHeader("Content-Type", "application/json")
                            .withBody("{invalid json}")));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "Content-Type: application/json\nТело: {invalid json} (невалидный JSON)");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса при невалидном JSON", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "По требованиям: проверяется только статус код, тело игнорируется");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Невалидный JSON проигнорирован\n" +
                            "✓ Важен только статус 200\n" +
                            "✓ LOGIN успешен");
        });
    }

    @Test
    @Tag("029")
    @DisplayName("Внешний сервис возвращает очень большой заголовок, меньше 8KB")
    @Severity(MINOR)
    void externalServiceReturns7KBHeader() {
        Allure.description("Проверка обработки больших заголовков (в пределах лимита)");

        String largeHeaderValue = "A".repeat(7000);

        Allure.step("1. Настройка мока с большим заголовком (7KB)", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(ok()
                            .withHeader("X-Large-Header", largeHeaderValue)));
            Allure.addAttachment("Размер заголовка", "text/plain", "~7KB (в пределах лимита)");
            Allure.addAttachment("Проверка", "text/plain", "Лимит обычно 8KB на заголовок");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса с большим заголовком", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Заголовок меньше 8KB должен быть обработан корректно");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Заголовок 7KB обработан успешно\n" +
                            "✓ В пределах лимита 8KB\n" +
                            "✓ LOGIN успешен");
        });
    }

    @Test
    @Tag("030")
    @DisplayName("Внешний сервис возвращает очень большой заголовок, больше 8KB")
    @Severity(MINOR)
    void externalServiceReturns10KBHeader() {
        Allure.description("Проверка обработки слишком больших заголовков (превышение лимита)");

        String largeHeaderValue = "A".repeat(10000);

        Allure.step("1. Настройка мока с очень большим заголовком (10KB)", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(ok()
                            .withHeader("X-Large-Header", largeHeaderValue)));
            Allure.addAttachment("Размер заголовка", "text/plain", "~10KB (превышает лимит 8KB)");
            Allure.addAttachment("Ожидание", "text/plain", "Должна быть ошибка валидации");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса с очень большим заголовком", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Заголовок больше 8KB должен вызвать ошибку");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Заголовок 10KB отклонен\n" +
                            "✓ Статус 400 Bad Request\n" +
                            "✓ Защита от слишком больших заголовков работает");
        });
    }

    @Test
    @Tag("031")
    @DisplayName("Внешний сервис возвращает множество заголовков")
    @Severity(MINOR)
    void externalServiceReturnsManyHeaders() {
        Allure.description("Проверка обработки ответа с большим количеством заголовков");

        Allure.step("1. Настройка мока со 100 кастомными заголовками", () -> {
            com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response = ok();
            for (int i = 0; i < 100; i++) {
                response = response.withHeader("X-Custom-Header-" + i, "value-" + i);
            }
            wireMockServer.stubFor(post("/auth").willReturn(response));
            Allure.addAttachment("Конфигурация", "text/plain", "100 кастомных заголовков");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса со множеством заголовков", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Множество заголовков должно обрабатываться корректно");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ 100 заголовков обработаны успешно\n" +
                            "✓ Лимит на количество заголовков не превышен\n" +
                            "✓ LOGIN успешен");
        });
    }

    @Test
    @Tag("032")
    @DisplayName("Восстановление после временной недоступности внешнего сервиса")
    @Severity(NORMAL)
    void recoveryAfterTemporaryExternalServiceOutage() {
        Allure.description("Проверка восстановления работы после временного отказа внешнего сервиса");

        String token = Allure.step("1. Генерация тестового токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Фаза 1: Внешний сервис недоступен (500)", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(serverError()));
            Allure.addAttachment("Состояние", "text/plain", "Внешний сервис возвращает 500");

            Allure.addAttachment("Ожидание", "text/plain", "LOGIN должен вернуть ошибку");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(500)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain", "✓ Ошибка при недоступном сервисе");
        });

        Allure.step("3. Фаза 2: Восстановление внешнего сервиса", () -> {
            wireMockServer.resetAll();
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            Allure.addAttachment("Состояние", "text/plain", "Внешний сервис восстановлен (200 OK)");
        });

        Allure.step("4. Фаза 3: Повторный LOGIN после восстановления", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "После восстановления сервиса LOGIN должен работать");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Система восстановила работу после сбоя\n" +
                            "✓ Токен все еще валиден\n" +
                            "✓ Resilience паттерн работает");
        });
    }

    @Test
    @Tag("033")
    @DisplayName("Обработка rate limiting от внешнего сервиса (429)")
    @Severity(NORMAL)
    @Tag("rate-limit")
    void externalServiceRateLimiting() {
        Allure.description("Проверка обработки ответа 429 Too Many Requests от внешнего сервиса");

        Allure.step("1. Настройка мока с rate limiting (429)", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(aResponse()
                            .withStatus(429)
                            .withHeader("Retry-After", "60")
                            .withBody("{\"error\":\"Too Many Requests\"}")));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "Статус: 429 Too Many Requests\n" +
                            "Retry-After: 60 секунд\n" +
                            "Тело: JSON с описанием ошибки");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса при rate limiting", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "При 429 от внешнего сервиса система должна вернуть 500");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(500)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                    .body("message", org.hamcrest.Matchers.notNullValue());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Rate limiting обработан корректно\n" +
                            "✓ Статус 500 Internal Server Error\n" +
                            "✓ Сообщение об ошибке присутствует");
        });
    }

    @Test
    @Tag("034")
    @DisplayName("Проверка формата сообщений об ошибках")
    @Severity(NORMAL)
    void errorMessageFormat() {
        Allure.description("Проверка формата и структуры сообщений об ошибках");

        Allure.step("1. Настройка мока с ошибкой", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(serverError()));
            Allure.addAttachment("Конфигурация", "text/plain", "Внешний сервис возвращает 500");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Проверка структуры ответа об ошибке", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Ошибка должна иметь корректный формат:\n" +
                            "1. Поле 'result' со значением 'ERROR'\n" +
                            "2. Поле 'message' непустое\n" +
                            "3. Только эти два поля");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(500)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                    .body("message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString()))
                    .body("$", org.hamcrest.Matchers.hasKey("result"))
                    .body("$", org.hamcrest.Matchers.hasKey("message"))
                    .body("$", org.hamcrest.Matchers.aMapWithSize(2));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Формат ошибки корректен:\n" +
                            "  ✓ result: 'ERROR' ✓\n" +
                            "  ✓ message: не пустой ✓\n" +
                            "  ✓ только 2 поля ✓");
        });
    }

    @Test
    @Tag("035")
    @DisplayName("Сохранение состояния после ошибки внешнего сервиса")
    @Severity(NORMAL)
    void statePreservationAfterExternalServiceError() {
        Allure.description("Проверка сохранения состояния сессии после ошибки внешнего сервиса ACTION");

        String token = Allure.step("1. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("2. Фаза 1: Успешный LOGIN", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            wireMockServer.stubFor(post("/doAction").willReturn(serverError()));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "/auth → 200 OK\n/doAction → 500 Server Error");

            given().formParam("token", token).formParam("action", "LOGIN").post("/endpoint");
            Allure.addAttachment("Результат", "text/plain", "✓ Сессия создана");
        });

        Allure.step("3. Фаза 2: ACTION с ошибкой внешнего сервиса", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Ошибка внешнего сервиса не должна закрывать сессию");

            given()
                    .formParam("token", token)
                    .formParam("action", "ACTION")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(500)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ ACTION вернул ошибку (ожидаемо)\n" +
                            "✓ Сессия должна сохраниться");
        });

        Allure.step("4. Фаза 3: Восстановление внешнего сервиса", () -> {
            wireMockServer.resetAll();
            wireMockServer.stubFor(post("/doAction").willReturn(ok()));
            Allure.addAttachment("Состояние", "text/plain", "Внешний сервис ACTION восстановлен");
        });

        Allure.step("5. Фаза 4: Повторный ACTION после восстановления", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Сессия должна сохраниться, ACTION должен работать");

            given()
                    .formParam("token", token)
                    .formParam("action", "ACTION")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ ACTION успешен после восстановления\n" +
                            "✓ Сессия сохранилась после ошибки\n" +
                            "✓ State preservation работает корректно");
        });
    }
}