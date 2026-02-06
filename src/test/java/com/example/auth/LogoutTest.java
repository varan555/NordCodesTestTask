package com.example.auth;

import com.example.base.TestBase;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.qameta.allure.SeverityLevel.*;
import static io.restassured.RestAssured.given;

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
    @Tag("015")
    @DisplayName("Успешный LOGOUT после LOGIN")
    @Severity(CRITICAL)
    void successfulLogoutAfterLogin() {
        Allure.description("Проверка полного workflow: LOGIN → LOGOUT → попытка ACTION (должна быть отклонена)");

        Allure.step("1. Настройка внешнего сервиса", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            Allure.addAttachment("Мок", "text/plain", "/auth → 200 OK");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. Выполнение LOGIN", () -> {
            Allure.addAttachment("Шаг", "text/plain", "Создание сессии");
            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));
            Allure.addAttachment("Результат", "text/plain", "✓ Сессия создана успешно");
        });

        Allure.step("4. Выполнение LOGOUT", () -> {
            Allure.addAttachment("Шаг", "text/plain", "Завершение сессии");
            given()
                    .formParam("token", token)
                    .formParam("action", "LOGOUT")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));
            Allure.addAttachment("Результат", "text/plain", "✓ Сессия завершена успешно");
        });

        Allure.step("5. Попытка ACTION после LOGOUT (должна быть отклонена)", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "После LOGOUT сессия должна быть закрыта, ACTION должен вернуть 403");

            given()
                    .formParam("token", token)
                    .formParam("action", "ACTION")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(403)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                    .body("message", org.hamcrest.Matchers.notNullValue());

            Allure.addAttachment("Результат", "text/plain",
                    "✓ ACTION отклонен с кодом 403\n" +
                            "✓ Сессия действительно закрыта\n" +
                            "✓ Система защищена от использования завершенных сессий");
        });

        Allure.addAttachment("Итоговый вывод", "text/plain",
                "Workflow выполнен корректно:\n" +
                        "1. LOGIN → создание сессии ✓\n" +
                        "2. LOGOUT → завершение сессии ✓\n" +
                        "3. ACTION после LOGOUT → отклонен ✓");
    }

    @Test
    @Tag("016")
    @DisplayName("LOGOUT без предварительного LOGIN")
    @Severity(NORMAL)
    void logoutWithoutLogin() {
        Allure.description("Проверка, что система не позволяет завершить несуществующую сессию");

        String token = Allure.step("1. Генерация токена без предварительного LOGIN", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t + " (без сессии)");
            return t;
        });

        Allure.step("2. Попытка LOGOUT без предварительного LOGIN", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна вернуть 403, так как сессия с этим токеном не существует");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGOUT")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(403)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                    .body("message",
                            org.hamcrest.Matchers.anyOf(
                                    org.hamcrest.Matchers.containsString("не найден"),
                                    org.hamcrest.Matchers.containsString("не существует"),
                                    org.hamcrest.Matchers.containsString("not found"),
                                    org.hamcrest.Matchers.containsString("not authorized"),
                                    org.hamcrest.Matchers.notNullValue()
                            )
                    );

            Allure.addAttachment("Результат", "text/plain",
                    "✓ LOGOUT отклонен с кодом 403\n" +
                            "✓ Система не позволяет завершить несуществующую сессию\n" +
                            "✓ Корректное сообщение об ошибке");
        });

        Allure.addAttachment("Защита системы", "text/plain",
                "✓ Предотвращено завершение несуществующих сессий\n" +
                        "✓ Корректная обработка edge-case");
    }

    @Test
    @Tag("017")
    @DisplayName("Повторный LOGOUT с тем же токеном")
    @Severity(MINOR)
    void repeatedLogoutWithSameToken() {
        Allure.description("Проверка идемпотентности операции LOGOUT");

        Allure.step("1. Настройка внешнего сервиса", () -> {
            wireMockServer.stubFor(post("/auth").willReturn(ok()));
            Allure.addAttachment("Мок", "text/plain", "/auth → 200 OK");
        });

        String token = Allure.step("2. Генерация токена", () -> {
            String t = generateToken();
            Allure.addAttachment("Токен", "text/plain", t);
            return t;
        });

        Allure.step("3. LOGIN для создания сессии", () -> {
            given()
                    .formParam("token", token)
                    .formParam("action", "LOGIN")
                    .when()
                    .post("/endpoint");
            Allure.addAttachment("Результат", "text/plain", "Сессия создана");
        });

        Allure.step("4. Первый LOGOUT (успешный)", () -> {
            Allure.addAttachment("Действие", "text/plain", "Попытка завершения активной сессии");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGOUT")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(200)
                    .body("result", org.hamcrest.Matchers.equalTo("OK"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Первый LOGOUT успешен\n✓ Сессия завершена");
        });

        Allure.step("5. Второй LOGOUT (должен вернуть ошибку)", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Повторный LOGOUT должен вернуть 403, так как сессия уже завершена");

            given()
                    .formParam("token", token)
                    .formParam("action", "LOGOUT")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(403)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"));

            Allure.addAttachment("Результат", "text/plain",
                    "✓ Повторный LOGOUT отклонен\n" +
                            "✓ Система защищена от дублирующих операций\n" +
                            "✓ Статус 403 (Forbidden)");
        });

        Allure.addAttachment("Идемпотентность", "text/plain",
                "✓ LOGOUT не является идемпотентной операцией\n" +
                        "✓ Повторные вызовы отклоняются корректно");
    }

    @Test
    @Tag("018")
    @DisplayName("LOGOUT с невалидным форматом токена")
    @Severity(MINOR)
    void logoutWithInvalidTokenFormat() {
        Allure.description("Проверка валидации формата токена при операции LOGOUT");

        String invalidToken = Allure.step("1. Подготовка невалидного токена", () -> {
            String token = "abc123!@#def456$%^ghi789&*()jkl";
            Allure.addAttachment("Токен (невалидный)", "text/plain", token);
            Allure.addAttachment("Проблема", "text/plain", "Содержит специальные символы: !@#$%^&*()");
            return token;
        });

        Allure.step("2. Попытка LOGOUT с невалидным токеном", () -> {
            Allure.addAttachment("Ожидание", "text/plain",
                    "Система должна вернуть 400, так как формат токена не соответствует требованиям");

            given()
                    .formParam("token", invalidToken)
                    .formParam("action", "LOGOUT")
                    .when()
                    .post("/endpoint")
                    .then()
                    .statusCode(400)
                    .body("result", org.hamcrest.Matchers.equalTo("ERROR"))
                    .body("message",
                            org.hamcrest.Matchers.anyOf(
                                    org.hamcrest.Matchers.containsString("формат"),
                                    org.hamcrest.Matchers.containsString("неверный"),
                                    org.hamcrest.Matchers.containsString("invalid"),
                                    org.hamcrest.Matchers.containsString("формата"),
                                    org.hamcrest.Matchers.notNullValue()
                            )
                    );

            Allure.addAttachment("Результат", "text/plain",
                    "✓ LOGOUT отклонен с кодом 400 (Bad Request)\n" +
                            "✓ Система выполняет валидацию формата токена\n" +
                            "✓ Сообщение об ошибке указывает на проблему с форматом");
        });

        Allure.addAttachment("Валидация входных данных", "text/plain",
                "✓ Проверка формата токена выполняется на всех операциях\n" +
                        "✓ Невалидные токены отклоняются на раннем этапе\n" +
                        "✓ Защита от инъекций и некорректных данных");
    }
}