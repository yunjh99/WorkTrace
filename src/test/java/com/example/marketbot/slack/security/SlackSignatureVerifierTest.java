package com.example.marketbot.slack.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class SlackSignatureVerifierTest {

    private static final String SIGNING_SECRET = "test-signing-secret";
    private final SlackSignatureVerifier verifier = new SlackSignatureVerifier(SIGNING_SECRET);

    @Test
    void acceptsValidSignature() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        byte[] body = "type=event_callback&event=test".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(timestamp, sign(timestamp, body), body)).isTrue();
    }

    @Test
    void rejectsSignatureWhenBodyWasChanged() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        byte[] originalBody = "command=%2Fworklog".getBytes(StandardCharsets.UTF_8);
        byte[] changedBody = "command=%2Fadmin".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(timestamp, sign(timestamp, originalBody), changedBody)).isFalse();
    }

    @Test
    void rejectsRequestOlderThanFiveMinutes() throws Exception {
        String timestamp = String.valueOf(Instant.now().minusSeconds(301).getEpochSecond());
        byte[] body = "payload={}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(timestamp, sign(timestamp, body), body)).isFalse();
    }

    private String sign(String timestamp, byte[] body) throws Exception {
        String baseString = "v0:" + timestamp + ":" + new String(body, StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "v0=" + HexFormat.of().formatHex(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)));
    }
}
