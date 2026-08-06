package com.example.marketbot.slack.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 원본 body를 검증한 뒤 컨트롤러에서도 다시 읽을 수 있게 하는 요청 래퍼입니다. */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;
    private final Map<String, String[]> formParameters;

    CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
        this.formParameters = parseFormParameters(request.getContentType(), body);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);

        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 동기 방식으로만 읽기 때문에 별도 콜백이 필요하지 않습니다.
            }

            @Override
            public int read() {
                return input.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public String getParameter(String name) {
        String[] values = formParameters.get(name);
        return values == null || values.length == 0 ? super.getParameter(name) : values[0];
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = formParameters.get(name);
        return values == null ? super.getParameterValues(name) : values.clone();
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> parameters = new LinkedHashMap<>(super.getParameterMap());
        formParameters.forEach((name, values) -> parameters.put(name, values.clone()));
        return Map.copyOf(parameters);
    }

    @Override
    public java.util.Enumeration<String> getParameterNames() {
        return java.util.Collections.enumeration(getParameterMap().keySet());
    }

    private static Map<String, String[]> parseFormParameters(String contentType, byte[] body) {
        if (contentType == null
                || !contentType.startsWith("application/x-www-form-urlencoded")
                || body.length == 0) {
            return Map.of();
        }

        Map<String, List<String>> values = new LinkedHashMap<>();
        String encodedForm = new String(body, StandardCharsets.UTF_8);
        for (String pair : encodedForm.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }

            int separator = pair.indexOf('=');
            String encodedName = separator < 0 ? pair : pair.substring(0, separator);
            String encodedValue = separator < 0 ? "" : pair.substring(separator + 1);
            String name = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
            String value = URLDecoder.decode(encodedValue, StandardCharsets.UTF_8);
            values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }

        Map<String, String[]> parameters = new LinkedHashMap<>();
        values.forEach((name, entries) -> parameters.put(name, entries.toArray(String[]::new)));
        return parameters;
    }
}
