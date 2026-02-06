package com.example.base;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import com.example.utils.TestDataGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class TestBase {

    protected static WireMockServer wireMockServer;
    protected static final String BASE_URL = "http://localhost:8080";
    protected static final String MOCK_URL = "http://localhost:8888";
    protected static final String API_KEY = "qazWSXedc";

    @BeforeAll
    static void setUpAll() {
        System.out.println("=== [INFO] Starting WireMock ===");

        int port = 8888;
        int maxRetries = 3;

        for (int i = 0; i < maxRetries; i++) {
            try {
                wireMockServer = new WireMockServer(options().port(port));
                wireMockServer.start();
                System.out.println("=== [INFO] WireMock started on port " + port + " ===");
                break;
            } catch (Exception e) {
                System.out.println("=== [WARN] Failed to start on port " + port + ", trying " + (port + 1) + " ===");
                port++;
                if (i == maxRetries - 1) {
                    throw new RuntimeException("Cannot start WireMock after " + maxRetries + " attempts", e);
                }
            }
        }

        configureWireMockDefaults();

        // Базовые фильтры для всех запросов
        RestAssured.filters(
                new RequestLoggingFilter(),
                new ResponseLoggingFilter()
        );

        // Проверяем, что WireMock отвечает
        try {
            String response = RestAssured
                    .given()
                    .baseUri("http://localhost:" + wireMockServer.port())
                    .when()
                    .get("/__admin")
                    .then()
                    .extract()
                    .asString();

            if (!response.contains("mappings")) {
                System.err.println("=== [ERROR] WireMock started but not responding correctly ===");
            }
        } catch (Exception e) {
            System.err.println("=== [ERROR] WireMock admin endpoint not accessible ===");
        }
    }

    static void configureWireMockDefaults() {
        // Настройка WireMock для ответа 200 на любые запросы по умолчанию
        wireMockServer.stubFor(post(urlPathMatching("/auth|/doAction"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        System.out.println("=== [SETUP] Starting test: " + testInfo.getDisplayName() +
                " in thread: " + Thread.currentThread().getName() + " ===");

        // Сброс WireMock перед каждым тестом
        if (wireMockServer != null) {
            wireMockServer.resetAll();
            configureWireMockDefaults();
        }

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterAll
    static void tearDownAll() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            System.out.println("=== [INFO] WireMock stopped ===");
        }
    }

    // ==================== ОСНОВНЫЕ МЕТОДЫ ДЛЯ ТЕСТОВ ====================

    /**
     * Получить ЧИСТЫЙ RequestSpecification для нового запроса
     * Каждый вызов создает новую независимую спецификацию
     */
    protected RequestSpecification given() {
        return RestAssured.given()
                .spec(baseSpecBuilder().build());
    }
    /**
    * Спецификаци без X-Api-Key

     */
    protected RequestSpecification givenWithoutApiKey() {
        return RestAssured.given()
                .baseUri(BASE_URL)
                .contentType(ContentType.URLENC)  // БЕЗ X-Api-Key!
                .accept(ContentType.JSON);
    }

    /**
     * Билдер базовой спецификации
     */
    private RequestSpecBuilder baseSpecBuilder() {
        return new RequestSpecBuilder()
                .setBaseUri(BASE_URL)
                .addHeader("X-Api-Key", API_KEY)
                .setContentType(ContentType.URLENC)
                .setAccept(ContentType.JSON)
                .addFilter(new AllureRestAssured());
    }

    /**
     * Получить RequestSpecification для быстрых запросов без Allure
     */
    protected RequestSpecification givenSimple() {
        return RestAssured.given()
                .baseUri(BASE_URL)
                .header("X-Api-Key", API_KEY)
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON);
    }

    /**
     * Получить RequestSpecification для WireMock запросов
     */
    protected RequestSpecification givenForWireMock() {
        return RestAssured.given()
                .baseUri("http://localhost:" + wireMockServer.port())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    /**
     * Создать новый независимый запрос с нуля
     * Полезно для последовательных запросов в одном тесте
     */
    protected RequestSpecification newRequest() {
        return RestAssured.given()
                .baseUri(BASE_URL)
                .header("X-Api-Key", API_KEY)
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .filter(new AllureRestAssured());
    }

    // ==================== УТИЛИТЫ ====================

    protected String generateToken() {
        return TestDataGenerator.generateValidToken();
    }

    /**
     * Настройка WireMock для успешных ответов
     */
    protected void setupWireMockForSuccess() {
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));
    }

    /**
     * Настройка WireMock для ошибочных ответов
     */
    protected void setupWireMockForError(int statusCode) {
        wireMockServer.stubFor(post("/auth").willReturn(aResponse().withStatus(statusCode)));
        wireMockServer.stubFor(post("/doAction").willReturn(aResponse().withStatus(statusCode)));
    }

    protected void verifyExternalServiceCall(String endpoint, int times) {
        wireMockServer.verify(times, postRequestedFor(urlEqualTo(endpoint)));
    }

    protected void verifyNoExternalServiceCall(String endpoint) {
        wireMockServer.verify(0, postRequestedFor(urlEqualTo(endpoint)));
    }

    /**
     * Метод для тестов с несколькими запросами
     * Гарантирует изоляцию между запросами
     */
    protected void executeIsolatedRequest(Runnable request) {
        RequestSpecification originalGlobalSpec = RestAssured.requestSpecification;
        try {
            // Временно сбрасываем глобальную спецификацию
            RestAssured.requestSpecification = null;
            request.run();
        } finally {
            // Восстанавливаем
            RestAssured.requestSpecification = originalGlobalSpec;
        }
    }

    /**
     * Выполнить запрос с полным логированием для отладки
     */
    protected void debugRequest(String token, String action) {
        System.out.println("=== [DEBUG REQUEST] ===");
        System.out.println("Token: " + token);
        System.out.println("Action: " + action);

        newRequest()
                .formParam("token", token)
                .formParam("action", action)
                .log().all()
                .when()
                .post("/endpoint")
                .then()
                .log().all();
    }
}