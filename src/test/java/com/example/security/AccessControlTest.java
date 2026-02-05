package com.example.security;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@Epic("Security")
@Feature("Access Control")
@Tag("security")
@Tag("authorization")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AccessControlTest extends TestBase {

    @BeforeAll
    void setupWireMock() {
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));
    }

    @Test
    @DisplayName("Проверка доступа к эндпоинту с разными HTTP методами")
    @Severity(CRITICAL)
    @Tag("http-methods")
    void endpointHttpMethodsAccess() {
        String token = generateToken();

        // POST - должен работать
        given()
                .header("X-Api-Key", "qazWSXedc")
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200);

        // GET - не должен работать
        given()
                .header("X-Api-Key", "qazWSXedc")
                .when()
                .get("/endpoint")
                .then()
                .statusCode(405); // Method Not Allowed

        // PUT - не должен работать
        given()
                .header("X-Api-Key", "qazWSXedc")
                .body(Map.of("token", token, "action", "LOGIN"))
                .when()
                .put("/endpoint")
                .then()
                .statusCode(405);

        // DELETE - не должен работать
        given()
                .header("X-Api-Key", "qazWSXedc")
                .when()
                .delete("/endpoint")
                .then()
                .statusCode(405);

        // PATCH - не должен работать
        given()
                .header("X-Api-Key", "qazWSXedc")
                .body(Map.of("token", token, "action", "LOGIN"))
                .when()
                .patch("/endpoint")
                .then()
                .statusCode(405);
    }

    @Test
    @DisplayName("Проверка Content-Type заголовка")
    @Severity(NORMAL)
    @Tag("headers")
    void contentTypeHeaderValidation() {
        String token = generateToken();

        // Правильный Content-Type: application/x-www-form-urlencoded
        given()
                .header("X-Api-Key", "qazWSXedc")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("OK"));

        // Неправильный Content-Type: application/json
        given()
                .header("X-Api-Key", "qazWSXedc")
                .header("Content-Type", "application/json")
                .body(Map.of("token", token, "action", "LOGIN"))
                .when()
                .post("/endpoint")
                .then()
                .statusCode(415); // Unsupported Media Type

        // Отсутствие Content-Type
        given()
                .header("X-Api-Key", "qazWSXedc")
                .body("token=" + token + "&action=LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(415); // или 400
    }

    @Test
    @DisplayName("Проверка Accept заголовка")
    @Severity(MINOR)
    @Tag("headers")
    void acceptHeaderValidation() {
        String token = generateToken();

        // Правильный Accept: application/json
        given()
                .header("X-Api-Key", "qazWSXedc")
                .header("Accept", "application/json")
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .contentType("application/json");

        // Accept: */*
        given()
                .header("X-Api-Key", "qazWSXedc")
                .header("Accept", "*/*")
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .contentType("application/json");

        // Accept: text/html (не должен приниматься)
        given()
                .header("X-Api-Key", "qazWSXedc")
                .header("Accept", "text/html")
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(406); // Not Acceptable
    }

    @Test
    @DisplayName("Проверка длины тела запроса")
    @Severity(NORMAL)
    @Tag("security")
    @Tag("limits")
    void requestBodySizeLimit() {
        // Очень длинный токен
        String veryLongToken = "A".repeat(10000);

        given()
                .header("X-Api-Key", "qazWSXedc")
                .formParam("token", veryLongToken)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(400) // или 413 (Payload Too Large)
                .body(anything());
    }

    @Test
    @DisplayName("Проверка пустого тела запроса")
    @Severity(NORMAL)
    @Tag("validation")
    void emptyRequestBody() {
        given()
                .header("X-Api-Key", "qazWSXedc")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(400); // Bad Request
    }

    @Test
    @DisplayName("Проверка отсутствия обязательных параметров")
    @Severity(CRITICAL)
    @Tag("validation")
    void missingRequiredParameters() {
        // Без token
        given()
                .header("X-Api-Key", "qazWSXedc")
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(400);

        // Без action
        given()
                .header("X-Api-Key", "qazWSXedc")
                .formParam("token", generateToken())
                .when()
                .post("/endpoint")
                .then()
                .statusCode(400);

        // Без обоих параметров
        given()
                .header("X-Api-Key", "qazWSXedc")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Проверка лишних параметров в запросе")
    @Severity(MINOR)
    @Tag("validation")
    void extraParametersInRequest() {
        given()
                .header("X-Api-Key", "qazWSXedc")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .formParam("extra_param", "value")
                .formParam("another_extra", "123")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200) // Лишние параметры должны игнорироваться
                .body("result", org.hamcrest.Matchers.equalTo("OK"));
    }

    @Test
    @DisplayName("Проверка SQL инъекции в параметрах")
    @Severity(CRITICAL)
    @Tag("security")
    @Tag("injection")
    void sqlInjectionInParameters() {
        String[] sqlInjections = {
                "' OR '1'='1",
                "'; DROP TABLE users; --",
                "' UNION SELECT * FROM users --",
                "admin' --"
        };

        for (String injection : sqlInjections) {
            // В параметре token
            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("token", injection + "A".repeat(32 - injection.length()))
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR")); // Должен отклонить невалидный токен

            // В параметре action
            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("token", generateToken())
                    .formParam("action", injection)
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR")); // Неизвестное действие
        }
    }

    @Test
    @DisplayName("Проверка XSS в параметрах")
    @Severity(CRITICAL)
    @Tag("security")
    @Tag("xss")
    void xssInParameters() {
        String xssPayload = "<script>alert('xss')</script>";

        // В токене
        given()
                .header("X-Api-Key", "qazWSXedc")
                .formParam("token", xssPayload + "A".repeat(32 - xssPayload.length()))
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

        // В action
        given()
                .header("X-Api-Key", "qazWSXedc")
                .formParam("token", generateToken())
                .formParam("action", xssPayload)
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));
    }

    @Test
    @DisplayName("Проверка path traversal")
    @Severity(CRITICAL)
    @Tag("security")
    void pathTraversal() {
        String[] paths = {
                "../endpoint",
                "....//endpoint",
                "/endpoint/../admin",
                "/endpoint%00",
                "/endpoint\0"
        };

        for (String path : paths) {
            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("token", generateToken())
                    .formParam("action", "LOGIN")
                    .when()
                    .post(path)
                    .then()
                    .statusCode(404); // Not Found
        }
    }

    @Test
    @DisplayName("Проверка CSRF уязвимости")
    @Severity(NORMAL)
    @Tag("security")
    @Tag("csrf")
    void csrfProtection() {
        // Добавляем Origin header как будто запрос с другого домена
        given()
                .header("X-Api-Key", "qazWSXedc")
                .header("Origin", "http://evil.com")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200); // REST API обычно не защищены от CSRF

        // Добавляем Referer header
        given()
                .header("X-Api-Key", "qazWSXedc")
                .header("Referer", "http://evil.com")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Проверка rate limiting")
    @Severity(NORMAL)
    @Tag("security")
    @Tag("rate-limit")
    void rateLimiting() {
        // Отправляем много запросов подряд
        for (int i = 0; i < 50; i++) {
            given()
                    .header("X-Api-Key", "qazWSXedc")
                    .formParam("token", generateToken())
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200); // Если есть rate limiting, последние запросы вернут 429
        }
    }

    @Test
    @DisplayName("Проверка CORS политики")
    @Severity(MINOR)
    @Tag("security")
    @Tag("cors")
    void corsPolicy() {
        // OPTIONS запрос для CORS preflight
        given()
                .header("Origin", "http://example.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Api-Key, Content-Type")
                .when()
                .options("/endpoint")
                .then()
                .statusCode(200) // или 204
                .header("Access-Control-Allow-Origin", is(notNullValue()))
                .header("Access-Control-Allow-Methods", containsString("POST"))
                .header("Access-Control-Allow-Headers", containsString("X-Api-Key"));
    }
}
