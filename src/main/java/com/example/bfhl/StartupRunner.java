package com.example.bfhl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class StartupRunner {

    private final Logger log = LoggerFactory.getLogger(StartupRunner.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String generateWebhookUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

    private final String registrationJson = """
            {
              "name": "Gopal verma",
              "regNo": "2211201211",
              "email": "2211201211@stu.manit.ac.in"
            }
            """;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            log.info("=== StartupRunner: calling generateWebhook ===");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> req = new HttpEntity<>(registrationJson, headers);

            // 1️⃣ Call generateWebhook API
            ResponseEntity<String> resp = restTemplate.postForEntity(generateWebhookUrl, req, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.error("generateWebhook failed: status={}, body={}", resp.getStatusCode(), resp.getBody());
                return;
            }

            Map<String, Object> respMap = objectMapper.readValue(resp.getBody(), new TypeReference<>() {});
            String webhook = findStringValue(respMap, "webhook");
            String accessToken = findStringValue(respMap, "accessToken");

            if (webhook == null || accessToken == null) {
                log.error("webhook or accessToken not found in response: {}", respMap);
                return;
            }

            // Clean token and webhook
            accessToken = accessToken.replace("\"", "").trim();
            webhook = webhook.trim();

            log.info("Webhook URL: {}", webhook);
            log.info("Access Token: {}", accessToken);

            // 2️⃣ Build SQL Query (replace with your actual answer if different)
            String finalQuery = "SELECT \n" +
                    "    p.AMOUNT AS SALARY,\n" +
                    "    CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME,\n" +
                    "    TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE,\n" +
                    "    d.DEPARTMENT_NAME\n" +
                    "FROM PAYMENTS p\n" +
                    "JOIN EMPLOYEE e ON p.EMP_ID = e.EMP_ID\n" +
                    "JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID\n" +
                    "WHERE DAY(p.PAYMENT_TIME) <> 1\n" +
                    "ORDER BY p.AMOUNT DESC\n" +
                    "LIMIT 1;";

            // 3️⃣ Prepare payload
            Map<String, String> payload = Map.of("fi na lQ ue Ry", finalQuery); // API key must match exactly
            String payloadJson = objectMapper.writeValueAsString(payload);

            HttpHeaders postHeaders = new HttpHeaders();
            postHeaders.setContentType(MediaType.APPLICATION_JSON);

            // ✅ FIX: Send raw token (no "Bearer")
            postHeaders.set("Authorization", accessToken);

            HttpEntity<String> postReq = new HttpEntity<>(payloadJson, postHeaders);

            // 4️⃣ Post solution to webhook
            log.info("Posting finalQuery to webhook...");
            ResponseEntity<String> postResp = restTemplate.postForEntity(webhook, postReq, String.class);

            log.info("POST to webhook status={}, body={}", postResp.getStatusCodeValue(), postResp.getBody());

        } catch (Exception e) {
            log.error("Error in startup flow", e);
        }
    }

    // Utility: recursively find key in JSON map
    private String findStringValue(Map<?, ?> map, String targetKey) {
        if (map == null) return null;
        String targetNormalized = targetKey.replaceAll("\\s+", "").toLowerCase();
        for (Object kObj : map.keySet()) {
            if (kObj == null) continue;
            String key = kObj.toString();
            String normalized = key.replaceAll("\\s+", "").toLowerCase();
            Object value = map.get(kObj);

            if (normalized.equals(targetNormalized)) {
                return value == null ? null : value.toString();
            }

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<?, ?> sub = (Map<?, ?>) value;
                String r = findStringValue(sub, targetKey);
                if (r != null) return r;
            }
        }
        return null;
    }
}
