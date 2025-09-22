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

  // === EDIT THESE ===
  // The assignment URL given in the PDF:
  private final String generateWebhookUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";
  // Registration JSON - replace name, regNo, email with your details.
  // Note: some assignment samples show the key "regNo " (with trailing space). The code below
  // will search keys ignoring spaces and case, so either "regNo" or "regNo " will work.
  private final String registrationJson = """
      {
        "name": "Gopal verma",
        "regNo": "2211201211",
        "email": "vermagopal416@gmail.com"
      }
      """;

  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    try {
      log.info("=== StartupRunner: calling generateWebhook ===");
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> req = new HttpEntity<>(registrationJson, headers);

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

      // parse regNo from the registration JSON
      Map<String, Object> regMap = objectMapper.readValue(registrationJson, new TypeReference<>() {});
      String regNo = findStringValue(regMap, "regNo"); // finds "regNo" or "regNo " etc.

      String finalQuery;
      finalQuery = "SELECT \n" + //
          "    p.AMOUNT AS SALARY,\n" + //
          "    CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME,\n" + //
          "    TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE,\n" + //
          "    d.DEPARTMENT_NAME\n" + //
          "FROM PAYMENTS p\n" + //
          "JOIN EMPLOYEE e ON p.EMP_ID = e.EMP_ID\n" + //
          "JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID\n" + //
          "WHERE DAY(p.PAYMENT_TIME) <> 1\n" + //
          "ORDER BY p.AMOUNT DESC\n" + //
          "LIMIT 1;";

      // Build payload and POST to webhook
      Map<String, String> payload = Map.of("finalQuery", finalQuery);
      String payloadJson = objectMapper.writeValueAsString(payload);

      HttpHeaders postHeaders = new HttpHeaders();
      postHeaders.setContentType(MediaType.APPLICATION_JSON);
      // Use Bearer token; if your assignment expects raw token change this line.
      postHeaders.setBearerAuth(accessToken);

      log.info("Posting finalQuery to webhook: {}", webhook);
      HttpEntity<String> postReq = new HttpEntity<>(payloadJson, postHeaders);
      ResponseEntity<String> postResp = restTemplate.postForEntity(webhook, postReq, String.class);
      log.info("POST to webhook status={}, body={}", postResp.getStatusCodeValue(), postResp.getBody());

    } catch (Exception e) {
      log.error("Error in startup flow", e);
    }
  }

  /**
   * Recursively search the map for a key matching targetKey (ignores whitespace and case),
   * and return the string value if found.
   */
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

  private boolean lastTwoDigitsAreOdd(String regNo) {
    if (regNo == null) return true; // default odd if missing
    // extract digits from the end
    String digits = regNo.replaceAll("\\D+", "");
    if (digits.isEmpty()) return true;
    String lastTwo = digits.length() > 2 ? digits.substring(digits.length() - 2) : digits;
    try {
      int n = Integer.parseInt(lastTwo);
      return (n % 2) != 0;
    } catch (NumberFormatException e) {
      return true;
    }
  }
}
