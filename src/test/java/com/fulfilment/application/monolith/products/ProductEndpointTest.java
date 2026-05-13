package com.fulfilment.application.monolith.products;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.IsNot.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductEndpointTest {

  // ─── LIST ALL ─────────────────────────────────────────────────────────────

  @Test
  @Order(1)
  public void testListAllProductsReturns200WithSeededData() {
    given()
            .when().get("product")
            .then()
            .statusCode(200)
            .body(
                    containsString("TONSTAD"),
                    containsString("KALLAX"),
                    containsString("BESTÅ"));
  }

  // ─── GET SINGLE ───────────────────────────────────────────────────────────

  @Test
  @Order(2)
  public void testGetSingleProductByIdReturns200() {
    given()
            .when().get("product/1")
            .then()
            .statusCode(200)
            .body("name", is("TONSTAD"))
            .body("id", is(1));
  }

  @Test
  @Order(3)
  public void testGetNonExistentProductReturns404() {
    given()
            .when().get("product/9999")
            .then()
            .statusCode(404)
            .body("code", is(404))
            .body("error", containsString("9999"));
  }

  // ─── CREATE ───────────────────────────────────────────────────────────────

  @Test
  @Order(4)
  public void testCreateProductReturns201() {
    String body = """
        {
          "name": "BILLY",
          "description": "Bookcase in white",
          "price": 59.99,
          "stock": 15
        }
        """;

    given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("product")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", is("BILLY"))
            .body("description", is("Bookcase in white"))
            .body("stock", is(15));
  }

  @Test
  @Order(5)
  public void testCreateProductWithPreSetIdReturns422() {
    String body = """
        {
          "id": 99,
          "name": "INVALID",
          "stock": 5
        }
        """;

    given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("product")
            .then()
            .statusCode(422)
            .body("code", is(422))
            .body("error", containsString("invalidly set"));
  }

  // ─── UPDATE ───────────────────────────────────────────────────────────────

  @Test
  @Order(6)
  public void testUpdateProductReturns200WithUpdatedFields() {
    String body = """
        {
          "name": "KALLAX-UPDATED",
          "description": "Updated storage unit",
          "price": 49.99,
          "stock": 8
        }
        """;

    given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("product/2")
            .then()
            .statusCode(200)
            .body("name", is("KALLAX-UPDATED"))
            .body("description", is("Updated storage unit"))
            .body("price", is(49.99f))
            .body("stock", is(8));
  }

  @Test
  @Order(7)
  public void testUpdateProductWithoutNameReturns422() {
    String body = """
        {
          "description": "No name provided",
          "stock": 5
        }
        """;

    given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("product/2")
            .then()
            .statusCode(422)
            .body("code", is(422))
            .body("error", containsString("Name was not set"));
  }

  @Test
  @Order(8)
  public void testUpdateNonExistentProductReturns404() {
    String body = """
        {
          "name": "GHOST",
          "stock": 1
        }
        """;

    given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("product/9999")
            .then()
            .statusCode(404)
            .body("code", is(404))
            .body("error", containsString("9999"));
  }

  // ─── DELETE ───────────────────────────────────────────────────────────────

  @Test
  @Order(9)
  public void testDeleteNonExistentProductReturns404() {
    given()
            .when().delete("product/9999")
            .then()
            .statusCode(404)
            .body("code", is(404))
            .body("error", containsString("9999"));
  }

  @Test
  @Order(10)
  public void testDeleteProductReturns204AndRemovedFromList() {
    // Delete TONSTAD (id=1)
    given()
            .when().delete("product/1")
            .then()
            .statusCode(204);

    // Verify it's gone from the list
    given()
            .when().get("product")
            .then()
            .statusCode(200)
            .body(
                    not(containsString("TONSTAD")),
                    containsString("KALLAX"),
                    containsString("BESTÅ"));
  }
}