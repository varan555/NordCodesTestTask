package com.example.auth;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;

@Epic("Authentication")
@Feature("Token Validation")
@Tag("auth")
@Tag("validation")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TokenValidationTest extends TestBase {

    @BeforeAll
    void setupWireMock() {
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ABCDEF1234567890ABCDEF1234567890",  // Все заглавные буквы и цифры
            "1234567890ABCDEF1234567890ABCDEF",  // Начинается с цифр
            "A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6",  // Чередование букв и цифр
            "00000000000000000000000000000000",  // Все нули
            "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"   // Все буквы Z
    })
    @DisplayName("Валидные форматы токенов: {arguments}")
    @Severity(CRITICAL)
    @Tag("positive")
    void validTokenFormats(String token) {
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdef1234567890abcdef1234567890",  // строчные буквы
            "ABCDEF1234567890ABCDEF123456789",   // 31 символ
            "ABCDEF1234567890ABCDEF12345678901", // 33 символа
            "ABCD EF1234567890ABCDEF1234567890", // содержит пробел
            "ABCD-EF1234567890ABCDEF1234567890", // содержит дефис
            "ABCDEF1234567890ABCDEF123456789!",  // содержит спецсимвол
            "",                                  // пустая строка
            "   ",                               // только пробелы
            "ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890" // очень длинный
    })
    @DisplayName("Невалидные форматы токенов: {arguments}")
    @Severity(CRITICAL)
    @Tag("negative")
    void invalidTokenFormats(String token) {
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @Test
    @DisplayName("Токен с русскими буквами")
    @Severity(MINOR)
    @Tag("negative")
    void tokenWithRussianLetters() {
        given()
                .formParam("token", "ПРИВЕТ1234567890ПРИВЕТ1234567890")
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @Test
    @DisplayName("Токен с китайскими иероглифами")
    @Severity(MINOR)
    @Tag("negative")
    void tokenWithChineseCharacters() {
        given()
                .formParam("token", "你好1234567890你好1234567890你好12")
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @ParameterizedTest
    @MethodSource("provideBoundaryTokens")
    @DisplayName("Граничные значения длины токена: {0}")
    @Severity(NORMAL)
    @Tag("boundary")
    void tokenLengthBoundaryValues(String description, String token, boolean expectedValid) {
        var response = given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .extract()
                .path("result");

        if (expectedValid) {
            Assertions.assertEquals("OK", response);
        } else {
            Assertions.assertEquals("ERROR", response);
        }
    }

    private static Stream<Arguments> provideBoundaryTokens() {
        return Stream.of(
                Arguments.of("29 символов", "ABCDEF1234567890ABCDEF12345678", false),
                Arguments.of("30 символов", "ABCDEF1234567890ABCDEF123456789", false),
                Arguments.of("31 символ", "ABCDEF1234567890ABCDEF1234567890", false), // На самом деле 31
                Arguments.of("32 символа", "ABCDEF1234567890ABCDEF1234567890", true), // Правильно 32
                Arguments.of("33 символа", "ABCDEF1234567890ABCDEF12345678901", false),
                Arguments.of("34 символа", "ABCDEF1234567890ABCDEF123456789012", false)
        );
    }

    @Test
    @DisplayName("Токен со спецсимволами SQL инъекции")
    @Severity(CRITICAL)
    @Tag("security")
    @Tag("injection")
    void tokenWithSqlInjection() {
        String[] sqlInjectionTokens = {
                "' OR '1'='1",
                "'; DROP TABLE users; --",
                "' UNION SELECT * FROM users --",
                "admin' --"
        };

        for (String token : sqlInjectionTokens) {
            // Дополняем до 32 символов
            String fullToken = token + "A".repeat(Math.max(0, 32 - token.length()));

            given()
                    .formParam("token", fullToken)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
        }
    }

    @Test
    @DisplayName("Токен с XSS payload")
    @Severity(CRITICAL)
    @Tag("security")
    @Tag("xss")
    void tokenWithXssPayload() {
        String xssToken = "<script>alert('xss')</script>ABCD";
        // Дополняем до 32 символов
        String fullToken = xssToken + "A".repeat(Math.max(0, 32 - xssToken.length()));

        given()
                .formParam("token", fullToken)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @Test
    @DisplayName("Токен с null character")
    @Severity(MINOR)
    @Tag("security")
    void tokenWithNullCharacter() {
        // Токен с null байтом
        String tokenWithNull = "ABC\0DEF1234567890ABCDEF123456789";

        given()
                .formParam("token", tokenWithNull)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @Test
    @DisplayName("Токен с максимально допустимыми символами (Z и 9)")
    @Severity(MINOR)
    @Tag("boundary")
    void tokenWithMaximumAllowedCharacters() {
        // Самые "старшие" допустимые символы
        String maxToken = "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ";
        String maxNumberToken = "99999999999999999999999999999999";

        given()
                .formParam("token", maxToken)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));

        given()
                .formParam("token", maxNumberToken)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }

    @Test
    @DisplayName("Токен с минимально допустимыми символами (A и 0)")
    @Severity(MINOR)
    @Tag("boundary")
    void tokenWithMinimumAllowedCharacters() {
        // Самые "младшие" допустимые символы
        String minLetterToken = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String minNumberToken = "00000000000000000000000000000000";

        given()
                .formParam("token", minLetterToken)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));

        given()
                .formParam("token", minNumberToken)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }
}