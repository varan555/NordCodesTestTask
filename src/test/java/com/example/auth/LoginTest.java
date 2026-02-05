package com.example.auth;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@Epic("Authentication")
@Feature("LOGIN Functionality")
@Tag("auth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LoginTest extends TestBase {


    @Test
    @DisplayName("Успешный LOGIN с валидным токеном")
    @Severity(CRITICAL)
    @Tag("positive")
    void successfulLogin() {

        // Настройка успешного ответа от внешнего сервиса
        wireMockServer.stubFor(post("/auth").willReturn(ok()));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));  // ← Теперь equalTo будет из org.hamcrest
    }

    @ParameterizedTest
    @ValueSource(strings = {"400", "401", "403", "404", "500", "503"})
    @DisplayName("LOGIN при разных ошибках внешнего сервиса: {arguments}")
    @Severity(NORMAL)
    @Tag("integration")
    void loginWithVariousExternalServiceErrors(String statusCode) {
        wireMockServer.stubFor(post("/auth")
                .willReturn(aResponse().withStatus(Integer.parseInt(statusCode))));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .body("result", equalTo("ERROR")); // При ошибке должен быть ERROR, не OK
    }

    @Test
    @DisplayName("LOGIN с задержкой ответа внешнего сервиса")
    @Severity(MINOR)
    @Tag("performance")
    void loginWithExternalServiceDelay() {
        wireMockServer.stubFor(post("/auth")
                .willReturn(ok().withFixedDelay(1500)));

        given()
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));
    }

    @Test
    @DisplayName("Повторный LOGIN с тем же токеном должен быть отклонён")
    @Severity(MINOR)
    @Tag("security")
    void repeatedLoginWithSameTokenShouldFail() {
        String token = generateToken();

        // Первый LOGIN - должен пройти
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        // Проверяем, что внешний сервис вызвался 1 раз
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));

        // Второй LOGIN - должен быть отклонён
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(409)
                .body("result", equalTo("ERROR"))
                .body("message", containsString("already exists"));

        // Проверяем, что второй раз внешний сервис НЕ вызывался
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));
    }
}

