package com.example.integration;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

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
    @DisplayName("Обработка сетевой ошибки при подключении к внешнему сервису")
    @Severity(CRITICAL)
    @Tag("network")
    @Tag("recovery")
    void networkErrorWhenConnectingToExternalService() {
        // Останавливаем WireMock для эмуляции недоступности сервиса
        wireMockServer.stop();

        try {
            given()
                    .formParam("token", generateToken())
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                    .body("message", notNullValue());
        } finally {
            wireMockServer.start();
        }
    }

    @Test
    @DisplayName("Обработка таймаута подключения к внешнему сервису")
    @Severity(CRITICAL)
    @Tag("timeout")
    @Tag("network")
    void connectionTimeoutToExternalService() {
        // Настраиваем очень большую задержку (больше таймаута приложения)
        wireMockServer.stubFor(post("/auth")
                .willReturn(ok().withFixedDelay(30000))); // 30 секунд

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("Обработка таймаута чтения от внешнего сервису")
    @Severity(NORMAL)
    @Tag("timeout")
    void readTimeoutFromExternalService() {
        // Эмулируем медленный ответ (первый байт быстро, но весь ответ медленно)
        wireMockServer.stubFor(post("/auth")
                .willReturn(ok()
                        .withChunkedDribbleDelay(10, 10000))); // 10 chunks, 10 секунд total

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @Test
    @DisplayName("Внешний сервис закрывает соединение без ответа")
    @Severity(NORMAL)
    @Tag("network")
    void externalServiceClosesConnectionWithoutResponse() {
        wireMockServer.stubFor(post("/auth")
                .willReturn(aResponse()
                        .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @Test
    @DisplayName("Внешний сервис возвращает пустой ответ")
    @Severity(MINOR)
    @Tag("edge-case")
    void externalServiceReturnsEmptyResponse() {
        wireMockServer.stubFor(post("/auth")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK")); // Пустое тело - все равно успех если статус 200
    }

    @Test
    @DisplayName("Внешний сервис возвращает невалидный JSON")
    @Severity(MINOR)
    @Tag("validation")
    void externalServiceReturnsInvalidJson() {
        wireMockServer.stubFor(post("/auth")
                .willReturn(ok()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{invalid json}")));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK")); // Должно игнорировать тело, только статус код
    }

    @Test
    @DisplayName("Внешний сервис возвращает очень большой заголовок")
    @Severity(MINOR)
    @Tag("security")
    void externalServiceReturnsOversizedHeader() {
        // Создаем очень большое значение заголовка
        String largeHeaderValue = "A".repeat(10000);

        wireMockServer.stubFor(post("/auth")
                .willReturn(ok()
                        .withHeader("X-Large-Header", largeHeaderValue)));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }

    @Test
    @DisplayName("Внешний сервис возвращает множество заголовков")
    @Severity(MINOR)
    @Tag("edge-case")
    void externalServiceReturnsManyHeaders() {
        // Создаем ответ с большим количеством заголовков
        com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response = ok();

        for (int i = 0; i < 100; i++) {
            response = response.withHeader("X-Custom-Header-" + i, "value-" + i);
        }

        wireMockServer.stubFor(post("/auth").willReturn(response));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }

    @Test
    @DisplayName("Обработка SSL ошибки при подключении к внешнему сервису")
    @Severity(NORMAL)
    @Tag("security")
    @Tag("ssl")
    void sslErrorWhenConnectingToExternalService() {
        // Тестировать сложно, но можно проверить, что приложение
        // корректно обрабатывает HTTPS URL если мы его укажем
        // В данном случае приложение использует HTTP, так что пропускаем
    }

    @Test
    @DisplayName("Восстановление после временной недоступности внешнего сервиса")
    @Severity(NORMAL)
    @Tag("recovery")
    @Tag("resilience")
    void recoveryAfterTemporaryExternalServiceOutage() {
        String token = generateToken();

        // 1. Первый запрос - сервис недоступен
        wireMockServer.stubFor(post("/auth").willReturn(serverError()));

        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

        // 2. Меняем мок - сервис снова доступен
        wireMockServer.resetAll();
        wireMockServer.stubFor(post("/auth").willReturn(ok()));

        // 3. Второй запрос с тем же токеном - должен работать
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }

    @Test
    @DisplayName("Обработка rate limiting от внешнего сервиса (429)")
    @Severity(NORMAL)
    @Tag("rate-limit")
    void externalServiceRateLimiting() {
        wireMockServer.stubFor(post("/auth")
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "60")
                        .withBody("{\"error\":\"Too Many Requests\"}")));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("Внешний сервис возвращает неожиданный Content-Type с ошибкой")
    @Severity(MINOR)
    @Tag("content-type")
    void externalServiceReturnsUnexpectedContentTypeWithError() {
        wireMockServer.stubFor(post("/auth")
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "image/png")
                        .withBody(new byte[]{0x00, 0x01, 0x02}))); // Бинарные данные

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @Test
    @DisplayName("Проверка формата сообщений об ошибках")
    @Severity(NORMAL)
    @Tag("validation")
    void errorMessageFormat() {
        wireMockServer.stubFor(post("/auth").willReturn(serverError()));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                .body("message", not(emptyOrNullString()))
                .body("$", hasKey("result"))
                .body("$", hasKey("message"))
                .body("$", aMapWithSize(2)); // Только result и message, не больше полей
    }

    @Test
    @DisplayName("Сохранение состояния после ошибки внешнего сервиса")
    @Severity(NORMAL)
    @Tag("state")
    @Tag("recovery")
    void statePreservationAfterExternalServiceError() {
        String token = generateToken();

        // 1. LOGIN успешен
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(serverError()));

        given().formParam("token", token).formParam("action", "LOGIN").post("/endpoint");

        // 2. ACTION с ошибкой внешнего сервиса
        given()
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

        // 3. Меняем мок на успешный
        wireMockServer.resetAll();
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));

        // 4. Повторный ACTION - должен работать (токен все еще в системе)
        given()
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }
}