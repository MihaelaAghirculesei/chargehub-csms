package io.github.mihaelaaghirculesei.csms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * springdoc brings Jackson 2 onto the classpath (docs/adr/0005). Our API responses must still be
 * written by Jackson 3.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JsonMessageConverterTest {

  @Autowired private RequestMappingHandlerAdapter handlerAdapter;

  @Test
  void writesJsonResponsesWithJackson3() {
    HttpMessageConverter<?> jsonConverter =
        handlerAdapter.getMessageConverters().stream()
            .filter(converter -> converter.canWrite(Map.class, MediaType.APPLICATION_JSON))
            .findFirst()
            .orElseThrow();

    assertThat(jsonConverter).isInstanceOf(JacksonJsonHttpMessageConverter.class);
  }
}
