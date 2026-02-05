package com.example.security;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@Epic("Security")
@Feature("API Key Authentication")
@Tag("security")
@Tag("authentication")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ApiKeyTest extends TestBase {

    @BeforeAll
    void setupWireMock() {
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
    }

    @Test
    @DisplayName("Успешный запрос с валидным API ключом")
    @Severity(CRITICAL)
    @Tag("positive")
    void validApiKey() {
        given()
                .header("X-Api-Key", "qazWSXedc")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }

    @Test
    @DisplayName("Запрос без API ключа")
    @Severity(CRITICAL)
    @Tag("negative")
    void missingApiKey() {
        given()
                // НЕ добавляем заголовок X-Api-Key
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403) // или 401
                .body(anything());
    }

    @Test
    @DisplayName("Неверный API ключ")
    @Severity(CRITICAL)
    @Tag("negative")
    void invalidApiKey() {
        given()
                .header("X-Api-Key", "WRONG_KEY")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403) // или 401
                .body(anything());
    }

    @Test
    @DisplayName("Пустой API ключ")
    @Severity(NORMAL)
    @Tag("negative")
    void emptyApiKey() {
        given()
                .header("X-Api-Key", "")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body(anything());
    }

    @Test
    @DisplayName("API ключ с пробелами")
    @Severity(NORMAL)
    @Tag("negative")
    void apiKeyWithSpaces() {
        given()
                .header("X-Api-Key", " qazWSXedc ")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body(anything());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "X-API-KEY",  // другой регистр
            "x-api-key",  // нижний регистр
            "X-API-Key",  // смешанный регистр
            "X_Api_Key",  // подчеркивания вместо дефисов
            "XApiKey"     // без дефисов
    })
    @DisplayName("Неверное имя заголовка API ключа: {arguments}")
    @Severity(MINOR)
    @Tag("negative")
    void wrongApiKeyHeaderName(String headerName) {
        given()
                .header(headerName, "qazWSXedc")  // Неправильное имя заголовка
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body(anything());
    }

    @Test
    @DisplayName("Несколько заголовков X-Api-Key")
    @Severity(MINOR)
    @Tag("edge-case")
    void multipleApiKeyHeaders() {
        given()
                .header("X-Api-Key", "qazWSXedc")
                .header("X-Api-Key", "ANOTHER_KEY")  // Второй заголовок
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403) // Должен отклонить при нескольких заголовках
                .body(anything());
    }

    @Test
    @DisplayName("API ключ с SQL инъекцией")
    @Severity(CRITICAL)
    @Tag("security")
    @Tag("injection")
    void apiKeyWithSqlInjection() {
        given()
                .header("X-Api-Key", "' OR '1'='1")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body(anything());
    }

    @Test
    @DisplayName("API ключ с XSS payload")
    @Severity(CRITICAL)
    @Tag("security")
    @Tag("xss")
    void apiKeyWithXssPayload() {
        given()
                .header("X-Api-Key", "<script>alert('xss')</script>")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body(anything());
    }

    @Test
    @DisplayName("Очень длинный API ключ")
    @Severity(MINOR)
    @Tag("security")
    void veryLongApiKey() {
        String longApiKey = "A".repeat(10000); // 10KB ключ

        given()
                .header("X-Api-Key", longApiKey)
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body(anything());
    }

    @Test
    @DisplayName("API ключ с null character")
    @Severity(CRITICAL)
    @Tag("security")
    void apiKeyWithNullCharacter() {
        given()
                .header("X-Api-Key", "qazWSX\0edc")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body(anything());
    }

    @Test
    @DisplayName("API ключ с бинарными данными")
    @Severity(CRITICAL)
    @Tag("security")
    void apiKeyWithBinaryData() {
        byte[] binaryData = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
        String binaryApiKey = new String(binaryData);

        given()
                .header("X-Api-Key", binaryApiKey)
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body(anything());
    }

    @Test
    @DisplayName("API ключ с разными кодировками")
    @Severity(MINOR)
    @Tag("security")
    void apiKeyWithDifferentEncodings() {
        String[] apiKeys = {
                "qazWSXedc",           // ASCII
                "qazWSXedc\u00E9",     // Latin-1
                "qazWSXedc\u4E2D",     // Unicode
                "qazWSXedc%20",        // URL encoded
                "qazWSXedc+"           // Plus sign
        };

        for (String apiKey : apiKeys) {
            given()
                    .header("X-Api-Key", apiKey)
                    .formParam("token", generateToken())
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(apiKey.equals("qazWSXedc") ? 200 : 403);
        }
    }

    @Test
    @DisplayName("Проверка регистрозависимости API ключа")
    @Severity(NORMAL)
    @Tag("validation")
    void apiKeyCaseSensitivity() {
        String[] caseVariations = {
                "qazWSXedc",  // оригинал
                "QAZWSXEDC",  // верхний регистр
                "qazwsxedc",  // нижний регистр
                "QazWsxEdc"   // смешанный регистр
        };

        for (String apiKey : caseVariations) {
            int expectedStatus = apiKey.equals("qazWSXedc") ? 200 : 403;

            given()
                    .header("X-Api-Key", apiKey)
                    .formParam("token", generateToken())
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(expectedStatus);
        }
    }
}
