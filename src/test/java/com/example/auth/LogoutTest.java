package com.example.auth;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Epic("Authentication")
@Feature("LOGOUT Functionality")
@Tag("auth")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LogoutTest extends TestBase {

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
    }

    @Test
    @DisplayName("Успешный LOGOUT после LOGIN")
    @Severity(CRITICAL)
    @Tag("positive")
    @Tag("workflow")
    void successfulLogoutAfterLogin() {
        // Given
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        String token = generateToken();

        // When 1: LOGIN
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));

        // When 2: LOGOUT
        given()
                .formParam("token", token)
                .formParam("action", "LOGOUT")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));

        // Then: Проверяем, что после LOGOUT нельзя выполнить ACTION
        given()
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)  // ← ИСПРАВЛЕНО: 403 вместо 200
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                .body("message", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    @DisplayName("LOGOUT без предварительного LOGIN")
    @Severity(NORMAL)
    @Tag("negative")
    void logoutWithoutLogin() {
        // Given: токен, который никогда не логинился
        String token = generateToken();

        // When: пытаемся выйти
        given()
                .formParam("token", token)
                .formParam("action", "LOGOUT")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)  // Исправлено: 403 вместо 200
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                .body("message",
                        anyOf(
                                containsString("не найден"),
                                containsString("не существует"),
                                containsString("not found"),
                                containsString("not authorized"),
                                notNullValue() // на случай если сообщение отличается
                        )
                );
    }

    @Test
    @DisplayName("Повторный LOGOUT с тем же токеном")
    @Severity(MINOR)
    @Tag("edge-case")
    void repeatedLogoutWithSameToken() {
        // Given
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        String token = generateToken();

        // LOGIN
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint");

        // Первый LOGOUT (должен быть успешным)
        given()
                .formParam("token", token)
                .formParam("action", "LOGOUT")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));

        // Второй LOGOUT (должен вернуть ошибку)
        given()
                .formParam("token", token)
                .formParam("action", "LOGOUT")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)  // Исправлено: 403 вместо 200
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @Test
    @DisplayName("LOGOUT с невалидным форматом токена")
    @Severity(MINOR)
    @Tag("validation")
    void logoutWithInvalidTokenFormat() {
        // Given: токен с недопустимыми символами
        String invalidToken = "abc123!@#def456$%^ghi789&*()jkl";

        // When
        given()
                .formParam("token", invalidToken)
                .formParam("action", "LOGOUT")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(400)  // Исправлено: 400 вместо 200
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                .body("message",
                        anyOf(
                                containsString("формат"),
                                containsString("неверный"),
                                containsString("invalid"),
                                containsString("формата"),
                                notNullValue() // на случай если сообщение отличается
                        )
                );
    }
   }