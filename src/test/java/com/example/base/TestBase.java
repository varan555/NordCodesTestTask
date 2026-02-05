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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class TestBase {

    protected static WireMockServer wireMockServer;
    protected static final String BASE_URL = "http://localhost:8080";
    protected static final String MOCK_URL = "http://localhost:8888";
    protected static final String API_KEY = "qazWSXedc";

    // ThreadLocal для изоляции спецификаций между потоками
    private static final ThreadLocal<RequestSpecification> THREAD_LOCAL_SPEC =
            ThreadLocal.withInitial(() -> null);

    // ThreadLocal для номера порта WireMock (если нужно динамическое распределение)
    private static final ThreadLocal<Integer> WIREMOCK_PORT =
            ThreadLocal.withInitial(() -> 8888);

    // Статический WireMock Server для всех тестов (если используется один порт)
    // ИЛИ ThreadLocal если каждый тест нужен отдельный WireMock

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

        // ❌ УДАЛЕНО: Глобальная настройка AllureRestAssured
        // RestAssured.filters(new AllureRestAssured());

        // Вместо этого настраиваем базовые фильтры (без Allure)
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

        // СОЗДАЕМ ThreadLocal спецификацию ДЛЯ КАЖДОГО ТЕСТА
        RequestSpecification spec = new RequestSpecBuilder()
                .setBaseUri(BASE_URL)
                .addHeader("X-Api-Key", API_KEY)
                .setContentType(ContentType.URLENC)
                .setAccept(ContentType.JSON)
                .addFilter(new AllureRestAssured()) // ✅ Добавляем Allure фильтр локально
                .build();

        // Сохраняем в ThreadLocal
        THREAD_LOCAL_SPEC.set(RestAssured.given().spec(spec));

        // НЕ устанавливаем глобально: RestAssured.requestSpecification = spec;

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        System.out.println("=== [TEARDOWN] Finished test: " + testInfo.getDisplayName() +
                " in thread: " + Thread.currentThread().getName() + " ===");

        // ОЧИЩАЕМ ThreadLocal после каждого теста
        THREAD_LOCAL_SPEC.remove();
    }

    @AfterAll
    static void tearDownAll() {
        // Очищаем все ThreadLocal
        THREAD_LOCAL_SPEC.remove();
        WIREMOCK_PORT.remove();

        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            System.out.println("=== [INFO] WireMock stopped ===");
        }
    }

    // ==================== УДОБНЫЕ МЕТОДЫ ДЛЯ ТЕСТОВ ====================

    /**
     * Получить RequestSpecification для текущего потока
     * Содержит AllureRestAssured фильтр
     */
    protected RequestSpecification given() {
        RequestSpecification spec = THREAD_LOCAL_SPEC.get();
        if (spec == null) {
            throw new IllegalStateException("RequestSpecification не инициализирован. " +
                    "Убедитесь, что тест выполняется в правильном контексте.");
        }
        return spec;
    }

    /**
     * Получить RequestSpecification без Allure фильтра
     * (например, для wiremock проверок)
     */
    protected RequestSpecification givenWithoutAllure() {
        return RestAssured.given()
                .baseUri(BASE_URL)
                .header("X-Api-Key", API_KEY)
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON);
    }

    /**
     * Получить RequestSpecification с кастомным Allure фильтром
     */
    protected RequestSpecification givenWithCustomAllure() {
        AllureRestAssured customFilter = new AllureRestAssured()
                .setRequestTemplate("http-request.ftl")
                .setResponseTemplate("http-response.ftl");

        return RestAssured.given()
                .baseUri(BASE_URL)
                .header("X-Api-Key", API_KEY)
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .filter(customFilter);
    }

    /**
     * Получить RequestSpecification для работы с WireMock
     */
    protected RequestSpecification givenForWireMock() {
        return RestAssured.given()
                .baseUri("http://localhost:" + wireMockServer.port())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    // ==================== УТИЛИТЫ ====================

    protected String generateToken() {
        return com.example.utils.TestDataGenerator.generateValidToken();
    }

    protected void setupWireMockForSuccess() {
        wireMockServer.stubFor(post("/auth").willReturn(ok()));
        wireMockServer.stubFor(post("/doAction").willReturn(ok()));
    }

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
     * Вспомогательный метод для отладки параллельных тестов
     */
    protected void logThreadInfo(String message) {
        System.out.println("[THREAD " + Thread.currentThread().getId() +
                " | " + Thread.currentThread().getName() + "] " + message);
    }

    /**
     * Метод для проверки, что Allure фильтр работает корректно
     */
    protected void validateAllureSetup() {
        System.out.println("=== [ALLURE VALIDATION] Thread: " + Thread.currentThread().getName() +
                ", Spec: " + (THREAD_LOCAL_SPEC.get() != null ? "OK" : "NULL") + " ===");
    }
}