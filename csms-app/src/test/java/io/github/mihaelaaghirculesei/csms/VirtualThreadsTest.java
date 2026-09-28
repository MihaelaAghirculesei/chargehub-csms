package io.github.mihaelaaghirculesei.csms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(VirtualThreadsTest.ThreadProbe.class)
class VirtualThreadsTest {

  @Autowired private RestTestClient client;

  @Test
  void servesRequestsOnVirtualThreads() {
    client
        .get()
        .uri("/test/thread-is-virtual")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(Boolean.class)
        .isEqualTo(true);
  }

  @RestController
  static class ThreadProbe {

    @GetMapping("/test/thread-is-virtual")
    boolean threadIsVirtual() {
      return Thread.currentThread().isVirtual();
    }
  }
}
