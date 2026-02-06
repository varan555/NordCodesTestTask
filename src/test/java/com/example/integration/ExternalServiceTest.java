package com.example.integration;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;

@Epic("Integration")
@Feature("External Service Communication")
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExternalServiceTest extends TestBase {

    @Test
    @Tag("036")
    @DisplayName("Верификация формата запроса к внешнему сервису")
    @Severity(NORMAL)
    void verifyRequestFormatToExternalService() {
        Allure.description("Проверка корректности формата запроса, отправляемого к внешнему сервису авторизации");

        Allure.step("1. Настройка мока с точной проверкой запроса", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "Ожидается POST /auth с проверкой:\n" +
                            "- Content-Type: application/x-www-form-urlencoded\n" +
                            "- Accept: application/json\n" +
                            "- Тело с параметром token");
        });

        String token = Allure.step("2. Генерация тестового токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка LOGIN запроса", () -> {
            Allure.addAttachment("Действие", "text/plain", "POST /endpoint с token и action=LOGIN");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint");

            Allure.addAttachment("Результат", "text/plain", "Запрос отправлен успешно");
        });

        Allure.step("4. Верификация запроса к внешнему сервису", () -> {
            Allure.addAttachment("Проверка", "text/plain",
                    "WireMock проверяет точный формат запроса к /auth");

            wireMockServer.verify(postRequestedFor(urlEqualTo("/auth"))
                    .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withRequestBody(equalTo("token=" + token)));

            Allure.addAttachment("Результат верификации", "text/plain",
                    "✓ Формат запроса корректен:\n" +
                            "  ✓ URL: /auth ✓\n" +
                            "  ✓ Content-Type: application/x-www-form-urlencoded ✓\n" +
                            "  ✓ Accept: application/json ✓\n" +
                            "  ✓ Тело содержит token ✓");
        });
    }

    @Test
    @Tag("037")
    @DisplayName("Внешний сервис возвращает нестандартные заголовки")
    @Severity(MINOR)
    void externalServiceReturnsCustomHeaders() {
        Allure.description("Проверка обработки кастомных заголовков от внешнего сервиса");

        Allure.step("1. Настройка мока с кастомными заголовками", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(ok()
                            .withHeader("X-Custom-Header", "value")
                            .withHeader("X-RateLimit-Limit", "100")));
            Allure.addAttachment("Заголовки ответа", "text/plain",
                    "X-Custom-Header: value\n" +
                            "X-RateLimit-Limit: 100");
            Allure.addAttachment("Цель", "text/plain",
                    "Проверка, что система корректно обрабатывает дополнительные заголовки");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса с кастомными заголовками", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Кастомные заголовки не должны влиять на работу системы");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Кастомные заголовки обработаны корректно\n" +
                            "✓ LOGIN успешен\n" +
                            "✓ Дополнительные заголовки не вызывают ошибок");
        });
    }

    @ParameterizedTest
    @MethodSource("provideResponseBodies")
    @Tag("038")
    @DisplayName("Обработка различных форматов тела ответа от внешнего сервиса")
    @Severity(NORMAL)
    void handleVariousResponseBodiesFromExternalService(String responseBody) {
        Allure.step("1. Настройка мока с телом ответа: " +
                (responseBody.length() > 50 ? responseBody.substring(0, 50) + "..." : responseBody), () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(ok()
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseBody)));
            Allure.addAttachment("Тело ответа", "text/plain", responseBody);
            Allure.addAttachment("Длина", "text/plain", responseBody.length() + " символов");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса и проверка", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Разные форматы тела должны обрабатываться корректно (главное - статус 200)");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Ответ с телом обработан корректно\n" +
                            "✓ Формат: " + describeResponseBody(responseBody) + "\n" +
                            "✓ Главное - статус 200, тело может быть любым");
        });
    }

    @Test
    @Tag("039")
    @DisplayName("Таймаут при обращении к внешнему сервису")
    @Severity(NORMAL)
    void externalServiceTimeout() {
        Allure.description("Проверка обработки медленного ответа от внешнего сервиса (4 секунды задержки)");

        Allure.step("1. Настройка мока с задержкой 4 секунды", () -> {
            wireMockServer.stubFor(post("/auth")
                    .willReturn(ok().withFixedDelay(4000)));
            Allure.addAttachment("Задержка", "text/plain", "4000ms (4 секунды)");
            Allure.addAttachment("Цель", "text/plain",
                    "Проверка, что система корректно обрабатывает медленные ответы");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Отправка запроса с измерением времени", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна дождаться ответа (если timeout настроен >4 секунд)");

            long startTime = System.currentTimeMillis();

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

            Allure.addAttachment("Время выполнения", "text/plain", duration + "ms");
            Allure.addAttachment("Результат", "text/plain",
                    "✓ Запрос выполнен за " + duration + "ms\n" +
                            "✓ Система дождалась медленного ответа\n" +
                            "✓ Timeout настроен корректно (>4 секунд)\n" +
                            "✓ LOGIN успешен");
        });
    }

    // Вспомогательные методы
    private static Stream<String> provideResponseBodies() {
        return Stream.of(
                "{\"status\":\"success\"}",
                "{\"code\":0,\"message\":\"OK\"}",
                "{}",
                "null",
                "{\"data\":{\"user\":{\"id\":123,\"name\":\"test\"}}}"
        );
    }

    private String describeResponseBody(String body) {
        if (body.equals("{}")) return "Пустой JSON объект";
        if (body.equals("null")) return "Значение null";
        if (body.contains("success")) return "JSON со статусом success";
        if (body.contains("code\":0")) return "JSON с кодом 0 и сообщением";
        if (body.contains("user")) return "JSON с вложенной структурой пользователя";
        return "Неизвестный формат (" + body.length() + " символов)";
    }
}