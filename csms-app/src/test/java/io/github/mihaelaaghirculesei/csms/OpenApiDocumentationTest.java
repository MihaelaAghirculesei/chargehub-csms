package io.github.mihaelaaghirculesei.csms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
class OpenApiDocumentationTest {

  @Autowired private RestTestClient client;

  @Test
  void publishesOpenApiDocument() {
    client
        .get()
        .uri("/v3/api-docs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.openapi")
        .isEqualTo("3.1.0")
        .jsonPath("$.info.title")
        .exists();
  }

  @Test
  void servesSwaggerUi() {
    client.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
  }
}
