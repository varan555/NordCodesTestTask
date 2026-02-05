package com.example.debug;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class ConnectionTest {

    @Test
    void testBasicRequest() {
        System.out.println("Testing connection to service...");

        given()
                .baseUri("http://localhost:8080")
                .header("X-Api-Key", "qazWSXedc")
                .formParam("token", "ABCDEF1234567890ABCDEF1234567890")
                .formParam("action", "LOGIN")
                .log().all() // Логируем весь запрос
                .when()
                .post("/endpoint")
                .then()
                .log().all() // Логируем весь ответ
                .statusCode(200);
    }
}
