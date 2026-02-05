package com.example.actions;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@Epic("Actions")
@Feature("ACTION Functionality")
@Tag("actions")
public class ActionTest extends TestBase {

    @Test
    @DisplayName("Успешное выполнение ACTION после LOGIN")
    @Severity(CRITICAL)
    @Tag("workflow")
    void successfulActionAfterLogin() {
        // Настраиваем оба эндпоинта
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));

        String token = generateToken();

        // 1. LOGIN
        given().formParam("token", token).formParam("action", "LOGIN")
                .when().post("/endpoint");

        // 2. ACTION
        given().formParam("token", token).formParam("action", "ACTION")
                .when().post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        // Проверяем вызовы
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/doAction")));
    }

    @Test
    void actionWithoutLoginShouldFail() {
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));

        given()
                .formParam("token", generateToken())
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/doAction")));
    }

    @Test
    @DisplayName("ACTION при ошибке внешнего сервиса /doAction")
    @Severity(NORMAL)
    @Tag("integration")
    void actionWhenExternalServiceReturnsError() {
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(serverError()));

        String token = generateToken();

        // LOGIN
        given().formParam("token", token).formParam("action", "LOGIN")
                .when().post("/endpoint");

        // ACTION с ошибкой
        given().formParam("token", token).formParam("action", "ACTION")
                .when().post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("ERROR"));
    }
    @Test
    void debugApiKeyTestFlow() {
        System.out.println("=== Диагностика ApiKeyTest ===");

        // 1. Что WireMock ожидает?
        System.out.println("WireMock порт: " + wireMockServer.port());

        // 2. Куда идёт запрос теста?
        given()
                .header("X-Api-Key", "qazWSXedc")
                .formParam("token", generateToken())
                .formParam("action", "LOGIN")
                .filter((request, response, ctx) -> {
                    System.out.println("Запрос на URL: " + request.getURI());
                    System.out.println("Заголовки: " + request.getHeaders());
                    return ctx.next(request, response);
                })
                .when()
                .post("/endpoint")
                .then()
                .log().all();

        // 3. Что пришло в WireMock?
        wireMockServer.getAllServeEvents().forEach(event -> {
            System.out.println("WireMock получил запрос: " +
                    event.getRequest().getUrl());
            System.out.println("С заголовками: " +
                    event.getRequest().getHeaders());
        });
    }
}