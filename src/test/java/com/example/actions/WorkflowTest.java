package com.example.actions;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@Epic("Actions")
@Feature("Complete Workflows")
@Tag("actions")
@Tag("workflow")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WorkflowTest extends TestBase {

    @Test
    @DisplayName("Полный workflow: LOGIN → ACTION → LOGOUT")
    @Severity(CRITICAL)
    @Tag("positive")
    @Tag("e2e")
    void fullWorkflowLoginActionLogout() {
        // Given
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));

        String token = generateToken();

        // When 1: LOGIN
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        // Verify external service was called
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));

        // When 2: ACTION (успешный)
        given()
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        // Verify external service was called
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/doAction")));

        // When 3: LOGOUT
        given()
                .formParam("token", token)
                .formParam("action", "LOGOUT")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        // Then: ACTION после LOGOUT должен падать
        given()
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("ERROR"));

        // Verify no additional calls to external service
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/doAction")));
    }

    @Test
    @DisplayName("Workflow с множественными ACTION между LOGIN и LOGOUT")
    @Severity(NORMAL)
    @Tag("positive")
    void workflowWithMultipleActions() {
        // Given
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));

        String token = generateToken();

        // LOGIN
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint");

        // Несколько ACTION подряд
        for (int i = 1; i <= 5; i++) {
            given()
                    .formParam("token", token)
                    .formParam("action", "ACTION")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", equalTo("OK"));
        }

        // LOGOUT
        given()
                .formParam("token", token)
                .formParam("action", "LOGOUT")
                .when()
                .post("/endpoint");

        // Проверяем количество вызовов
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));
        wireMockServer.verify(5, postRequestedFor(urlEqualTo("/doAction")));
    }

    @Test
    @DisplayName("Workflow с ошибкой внешнего сервиса при ACTION")
    @Severity(NORMAL)
    @Tag("negative")
    @Tag("integration")
    void workflowWithExternalServiceErrorOnAction() {
        // Given: LOGIN успешен, но ACTION возвращает ошибку
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(serverError()));

        String token = generateToken();

        // LOGIN (успешно)
        given()
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        // ACTION (с ошибкой)
        given()
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                .body("message", org.hamcrest.Matchers.notNullValue());

        // После ошибки ACTION токен все еще должен быть валидным
        // (можно выполнить еще один ACTION или LOGOUT)
        given()
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("ERROR")); // Все еще ошибка, т.к. мок не изменился

        // LOGOUT должен работать
        given()
                .formParam("token", token)
                .formParam("action", "LOGOUT")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));
    }

    @Test
    @DisplayName("Workflow с параллельными запросами разных токенов")
    @Severity(NORMAL)
    @Tag("concurrency")
    @Tag("integration")
    void workflowWithMultipleTokensParallel() {
        // Given
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));

        String token1 = generateToken();
        String token2 = generateToken();
        String token3 = generateToken();

        // Параллельные LOGIN разных токенов
        given().formParam("token", token1).formParam("action", "LOGIN").post("/endpoint");
        given().formParam("token", token2).formParam("action", "LOGIN").post("/endpoint");
        given().formParam("token", token3).formParam("action", "LOGIN").post("/endpoint");

        // ACTION для каждого токена
        given().formParam("token", token1).formParam("action", "ACTION").post("/endpoint");
        given().formParam("token", token2).formParam("action", "ACTION").post("/endpoint");
        given().formParam("token", token3).formParam("action", "ACTION").post("/endpoint");

        // LOGOUT для двух токенов
        given().formParam("token", token1).formParam("action", "LOGOUT").post("/endpoint");
        given().formParam("token", token2).formParam("action", "LOGOUT").post("/endpoint");

        // Проверяем состояния
        given().formParam("token", token1).formParam("action", "ACTION")
                .post("/endpoint").then().body("result", equalTo("ERROR"));
        given().formParam("token", token2).formParam("action", "ACTION")
                .post("/endpoint").then().body("result", equalTo("ERROR"));
        given().formParam("token", token3).formParam("action", "ACTION")
                .post("/endpoint").then().body("result", equalTo("OK"));

        // Проверяем вызовы внешнего сервиса
        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/auth")));
        wireMockServer.verify(5, postRequestedFor(urlEqualTo("/doAction"))); // 3 успешных + 2 после logout
    }

    @Test
    @DisplayName("Workflow: LOGIN → ACTION → LOGOUT → Повторный LOGIN")
    @Severity(NORMAL)
    @Tag("reusability")
    void workflowWithReLogin() {
        // Given
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));

        String token = generateToken();

        // Цикл 1
        given().formParam("token", token).formParam("action", "LOGIN").post("/endpoint");
        given().formParam("token", token).formParam("action", "ACTION").post("/endpoint");
        given().formParam("token", token).formParam("action", "LOGOUT").post("/endpoint");

        // Цикл 2 с тем же токеном
        given().formParam("token", token).formParam("action", "LOGIN").post("/endpoint");
        given().formParam("token", token).formParam("action", "ACTION").post("/endpoint");
        given().formParam("token", token).formParam("action", "LOGOUT").post("/endpoint");

        // Проверяем вызовы
        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/auth")));
        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/doAction")));
    }

    @Test
    @DisplayName("Workflow с чередованием ACTION и повторного LOGIN")
    @Severity(MINOR)
    @Tag("edge-case")
    void workflowWithActionBetweenLogins() {
        // Given
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));

        String token = generateToken();

        // LOGIN 1
        given().formParam("token", token).formParam("action", "LOGIN").post("/endpoint");
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));

        // ACTION 1
        given().formParam("token", token).formParam("action", "ACTION").post("/endpoint");
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/doAction")));

        // LOGIN 2 (повторный, не должен вызывать внешний сервис если уже залогинен)
        given().formParam("token", token).formParam("action", "LOGIN").post("/endpoint");
        // Проверяем, что внешний сервис НЕ вызывался повторно
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));

        // ACTION 2
        given().formParam("token", token).formParam("action", "ACTION").post("/endpoint");
        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/doAction")));
    }

    @Test
    @DisplayName("Workflow с прерыванием на середине")
    @Severity(MINOR)
    @Tag("error-handling")
    void workflowInterruptedByExternalServiceError() {
        // Given: первый LOGIN успешен, второй возвращает ошибку
        wireMockServer.stubFor(post("/auth")
                .willReturn(ok())); // Первый вызов

        String token1 = generateToken();
        String token2 = generateToken();

        // LOGIN token1 (успешно)
        given().formParam("token", token1).formParam("action", "LOGIN")
                .post("/endpoint").then().body("result", equalTo("OK"));

        // Меняем мок для следующих вызовов
        wireMockServer.resetAll();
        wireMockServer.stubFor(post("/auth").willReturn(serverError()));

        // LOGIN token2 (с ошибкой)
        given().formParam("token", token2).formParam("action", "LOGIN")
                .post("/endpoint").then().body("result", equalTo("ERROR"));

        // Проверяем, что token1 все еще работает
        wireMockServer.resetAll();
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));

        given().formParam("token", token1).formParam("action", "ACTION")
                .post("/endpoint").then().body("result", equalTo("OK"));

        // token2 не должен работать
        given().formParam("token", token2).formParam("action", "ACTION")
                .post("/endpoint").then().body("result", equalTo("ERROR"));
    }
}