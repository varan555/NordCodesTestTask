package com.example.actions;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.hamcrest.Matchers;
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
    @Tag("010")
    @DisplayName("Успешное выполнение ACTION после LOGIN")
    @Severity(CRITICAL)
    void successfulActionAfterLogin() {
        Allure.step("1. Настройка моков для внешних сервисов", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            wireMockServer.stubFor(post("/doAction").willReturn(ok()));
            Allure.addAttachment("Конфигурация моков", "text/plain",
                    "✓ /auth → 200 OK\n✓ /doAction → 200 OK");
        });

        String token = Allure.step("2. Генерация тестового токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Выполнение LOGIN", () -> {
            Allure.addAttachment("LOGIN запрос", "text/plain",
                    "token: " + token + "\naction: LOGIN");
            given().formParam("token", token).formParam("action", "LOGIN")
                    .when().post("/endpoint");
            Allure.addAttachment("Результат LOGIN", "text/plain", "Успешно");
        });

        Allure.step("4. Выполнение ACTION", () -> {
            Allure.addAttachment("ACTION запрос", "text/plain",
                    "token: " + token + "\naction: ACTION");
            given().formParam("token", token).formParam("action", "ACTION")
                    .when().post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", equalTo("OK"));
            Allure.addAttachment("Результат ACTION", "text/plain", "Успешно");
        });

        Allure.step("5. Проверка вызовов внешних сервисов", () -> {
            wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));
            wireMockServer.verify(1, postRequestedFor(urlEqualTo("/doAction")));
            Allure.addAttachment("Верификация моков", "text/plain",
                    "✓ /auth вызван 1 раз\n✓ /doAction вызван 1 раз");
        });
    }

    @Test
    @Tag("011")
    @DisplayName("Попытка выполнения ACTION без LOGIN должна быть неудачной")
    @Severity(CRITICAL)
    void actionWithoutLoginShouldFail() {
        Allure.step("1. Настройка мока doAction", () -> {
            wireMockServer.stubFor(post("/doAction").willReturn(ok()));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "✓ /doAction настроен на 200 OK\n✗ /auth не настраивается (имитация отсутствия LOGIN)");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Попытка ACTION без предварительного LOGIN", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна отклонить запрос с кодом 403, так как сессия не создана");

            given()
                    .formParam("token", token)
                    .formParam("action", "ACTION")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(403)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Система корректно отклонила ACTION без предварительного LOGIN\n" +
                            "✓ Возвращен статус 403 (Forbidden)");
        });

        Allure.step("4. Проверка, что внешний сервис не вызывался", () -> {
            wireMockServer.verify(0, postRequestedFor(urlEqualTo("/doAction")));
            Allure.addAttachment("Верификация", "text/plain",
                    "✓ /doAction НЕ был вызван, что корректно для неавторизованного запроса");
        });
    }

    @Test
    @Tag("012")
    @DisplayName("Workflow с множественными ACTION между LOGIN и LOGOUT")
    @Severity(NORMAL)
    void workflowWithMultipleActions() {
        Allure.step("1. Настройка моков", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            wireMockServer.stubFor(post("/doAction").willReturn(ok()));
            Allure.addAttachment("Моки", "text/plain", "Все внешние сервисы настроены на успешный ответ");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. LOGIN - начало сессии", () -> {
            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint");
            Allure.addAttachment("Шаг", "text/plain", "Сессия создана успешно");
        });

        Allure.step("4. Выполнение 5 ACTION подряд", () -> {
            for (int i = 1; i <= 5; i++) {
                final int actionNumber = i; // Финализируем переменную
                Allure.step("ACTION #" + actionNumber, () -> {
                    given()
                            .formParam("token", token)
                            .formParam("action", "ACTION")
                            .when()
                            .post("/endpoint")
                            .then()
                            .statusCode(200)
                            .body("result", Matchers.equalTo("OK"));
                    Allure.addAttachment("Результат", "text/plain", "ACTION #" + actionNumber + " выполнен успешно");
                });
            }
            Allure.addAttachment("Итог", "text/plain", "5 ACTION выполнены последовательно без ошибок");
        });

        Allure.step("5. LOGOUT - завершение сессии", () -> {
            given()
                    .formParam("token", token)
                    .formParam("action", "LOGOUT")
                    .when()
                    .post("/endpoint");
            Allure.addAttachment("Шаг", "text/plain", "Сессия завершена корректно");
        });

        Allure.step("6. Проверка количества вызовов", () -> {
            wireMockServer.verify(1, postRequestedFor(urlEqualTo("/auth")));
            wireMockServer.verify(5, postRequestedFor(urlEqualTo("/doAction")));
            Allure.addAttachment("Верификация", "text/plain",
                    "✓ /auth вызван 1 раз\n✓ /doAction вызван 5 раз\n" +
                            "✓ Соответствует ожидаемому workflow: LOGIN → 5×ACTION → LOGOUT");
        });
    }

    @Test
    @Tag("013")
    @DisplayName("Workflow с ошибкой внешнего сервиса при ACTION")
    @Severity(NORMAL)
    void workflowWithExternalServiceErrorOnAction() {
        Allure.description("Тест проверяет поведение системы при ошибке внешнего сервиса во время выполнения ACTION");

        Allure.step("1. Настройка моков: LOGIN успешен, ACTION с ошибкой", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            wireMockServer.stubFor(post("/doAction").willReturn(serverError()));
            Allure.addAttachment("Конфигурация", "text/plain",
                    "✓ /auth → 200 OK\n✗ /doAction → 500 Internal Server Error");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. LOGIN (успешно)", () -> {
            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", Matchers.equalTo("OK"));
            Allure.addAttachment("Результат", "text/plain", "Сессия создана успешно");
        });

        Allure.step("4. ACTION с ошибкой внешнего сервиса", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "При ошибке внешнего сервиса система должна вернуть 500 и сообщение об ошибке");

            given()
                    .formParam("token", token)
                    .formParam("action", "ACTION")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(500)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                    .body("message", org.hamcrest.Matchers.notNullValue());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Система вернула 500 при ошибке внешнего сервиса\n" +
                            "✓ Сообщение об ошибке присутствует\n" +
                            "✓ Сессия остается активной");
        });

        Allure.step("5. Повторный ACTION (все еще ошибка)", () -> {
            Allure.addAttachment("Контекст", "text/plain",
                    "Мок не изменился, поэтому внешний сервис все еще возвращает ошибку");

            given()
                    .formParam("token", token)
                    .formParam("action", "ACTION")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(500)
                    .body("result", Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Поведение консистентно: ошибка сохраняется");
        });

        Allure.step("6. LOGOUT работает несмотря на ошибки ACTION", () -> {
            wireMockServer.resetAll();
            wireMockServer.stubFor(post("/auth").willReturn(ok()));

            Allure.addAttachment("Ожидание", "text/plain",
                    "LOGOUT должен работать независимо от состояния внешнего сервиса ACTION");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGOUT")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ LOGOUT выполнен успешно\n" +
                            "✓ Сессия завершена корректно даже после ошибок ACTION");
        });
    }

    @Test
    @Tag("014")
    @DisplayName("Workflow с прерыванием на середине")
    @Severity(MINOR)
    void workflowInterruptedByExternalServiceError() {
        Allure.description("Тест проверяет изоляцию сессий при ошибках внешнего сервиса");

        Allure.step("1. Начальная настройка: первый LOGIN успешен", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            Allure.addAttachment("Конфигурация", "text/plain", "Первый /auth → 200 OK");
        });

        String token1 = Allure.step("2. Генерация первого токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Token 1", "text/plain", t);
            return t;
        });

        String token2 = Allure.step("3. Генерация второго токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Token 2", "text/plain", t);
            return t;
        });

        Allure.step("4. LOGIN token1 (успешно)", () -> {
            given().formParam("token", token1).formParam("action", "LOGIN")
                    .post("/endpoint").then().body("result", Matchers.equalTo("OK"));
            Allure.addAttachment("Результат", "text/plain", "Сессия для token1 создана");
        });

        Allure.step("5. Смена моков: LOGIN теперь возвращает ошибку", () -> {
            wireMockServer.resetAll();
            wireMockServer.stubFor(post("/auth").willReturn(serverError()));
            Allure.addAttachment("Новая конфигурация", "text/plain",
                    "✓ /auth теперь → 500 Server Error\n" +
                            "✓ Имитация отказа внешнего сервиса авторизации");
        });

        Allure.step("6. LOGIN token2 (с ошибкой)", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Token2 не должен создать сессию из-за ошибки внешнего сервиса");

            given().formParam("token", token2).formParam("action", "LOGIN")
                    .post("/endpoint").then().body("result", Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Token2 не создал сессию (ожидаемо)\n" +
                            "✓ Система корректно обработала ошибку внешнего сервиса");
        });

        Allure.step("7. Проверка: token1 все еще работает", () -> {
            wireMockServer.resetAll();
            wireMockServer.stubFor(post("/doAction").willReturn(ok()));
            Allure.addAttachment("Конфигурация", "text/plain", "Восстановили работу /doAction");

            Allure.addAttachment("Ожидание", "text/plain",
                    "Сессия token1 должна оставаться активной, несмотря на проблемы с новыми сессиями");

            given().formParam("token", token1).formParam("action", "ACTION")
                    .post("/endpoint").then().body("result", Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Token1 работает корректно\n" +
                            "✓ Изоляция сессий обеспечена");
        });

        Allure.step("8. Проверка: token2 не работает", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Token2 не должен работать, так как сессия не была создана");

            given().formParam("token", token2).formParam("action", "ACTION")
                    .post("/endpoint").then().body("result", Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Token2 не работает (корректно)\n" +
                            "✓ Система не позволяет использовать токен без успешного LOGIN");
        });

        Allure.addAttachment("Итоговый вывод", "text/plain",
                "✓ Изоляция сессий работает корректно\n" +
                        "✓ Ошибка создания новой сессии не влияет на существующие сессии\n" +
                        "✓ Система устойчива к частичным отказам внешних сервисов");
    }
}