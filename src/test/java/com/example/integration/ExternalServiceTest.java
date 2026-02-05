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
import static org.hamcrest.CoreMatchers.*;

@Epic("Integration")
@Feature("External Service Communication")
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExternalServiceTest extends TestBase {

    @Test
    @DisplayName("Верификация формата запроса к внешнему сервису")
    @Severity(NORMAL)
    @Tag("validation")
    void verifyRequestFormatToExternalService() {
        wireMockServer.stubFor(post("/auth").willReturn(ok()));

        String token = generateToken();

        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint");

        // Проверяем точный формат запроса
        wireMockServer.verify(postRequestedFor(urlEqualTo("/auth"))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalTo("token=" + token)));
    }

    @Test
    @DisplayName("Внешний сервис возвращает нестандартные заголовки")
    @Severity(MINOR)
    @Tag("edge-case")
    void externalServiceReturnsCustomHeaders() {
        wireMockServer.stubFor(post("/auth")
                .willReturn(ok()
                        .withHeader("X-Custom-Header", "value")
                        .withHeader("X-RateLimit-Limit", "100")));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }

    @ParameterizedTest
    @MethodSource("provideResponseBodies")
    @DisplayName("Внешний сервис возвращает разное тело: {arguments}")
    @Severity(MINOR)
    @Tag("edge-case")
    void externalServiceReturnsVariousResponseBodies(String responseBody) {
        wireMockServer.stubFor(post("/auth")
                .willReturn(ok()
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK")); // Должно работать независимо от тела
    }

    private static Stream<String> provideResponseBodies() {
        return Stream.of(
                "{\"status\":\"success\"}",
                "{\"code\":0,\"message\":\"OK\"}",
                "{}",
                "null",
                "{\"data\":{\"user\":{\"id\":123,\"name\":\"test\"}}}"
        );
    }

    @Test
    @DisplayName("Таймаут при обращении к внешнему сервису")
    @Severity(NORMAL)
    @Tag("performance")
    @Tag("timeout")
    @Disabled("Долгий тест, запускать вручную")
    void externalServiceTimeout() {
        wireMockServer.stubFor(post("/auth")
                .willReturn(ok().withFixedDelay(10000))); // 10 секунд

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR")); // Ожидаем ошибку таймаута
    }
}