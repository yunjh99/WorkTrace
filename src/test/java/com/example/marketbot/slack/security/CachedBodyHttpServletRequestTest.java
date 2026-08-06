package com.example.marketbot.slack.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CachedBodyHttpServletRequestTest {

    @Test
    void restoresFormParametersFromCachedBody() {
        String body = "payload=%7B%22type%22%3A%22block_actions%22%7D&tag=one&tag=two";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/slack/interaction");
        request.setContentType("application/x-www-form-urlencoded;charset=UTF-8");

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(
                request,
                body.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(cachedRequest.getParameter("payload"))
                .isEqualTo("{\"type\":\"block_actions\"}");
        assertThat(cachedRequest.getParameterValues("tag")).containsExactly("one", "two");
        assertThat(cachedRequest.getParameterMap()).containsKeys("payload", "tag");
    }
}
