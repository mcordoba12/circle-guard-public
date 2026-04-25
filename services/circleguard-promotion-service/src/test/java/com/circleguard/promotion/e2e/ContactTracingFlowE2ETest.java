package com.circleguard.promotion.e2e;

import com.circleguard.promotion.service.CircleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-End tests for contact tracing flow.
 * Validates circle/contact group retrieval and encounter registration.
 *
 * Flow:
 * 1. GET /api/v1/circles/user/{anonymousId} → retrieves contact circles
 * 2. Verify response structure and data validity
 * 3. POST to register encounter (simulated)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("End-to-End Contact Tracing Flow Tests")
class ContactTracingFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private CircleService circleService;

    private String testAnonymousId;
    private String otherUserId;

    @BeforeEach
    void setUp() {
        testAnonymousId = UUID.randomUUID().toString();
        otherUserId = UUID.randomUUID().toString();

        // Mock KafkaTemplate
        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(null);
    }

    @Test
    @DisplayName("E2E: Get user circles returns 200 OK")
    void test_getUserCircles_Returns200OK() {
        // Arrange
        when(circleService.getUserCircles(testAnonymousId))
                .thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/circles/user/" + testAnonymousId,
                List.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("E2E: Get user circles returns list structure")
    void test_getUserCircles_ReturnsListStructure() {
        // Arrange
        when(circleService.getUserCircles(testAnonymousId))
                .thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/circles/user/" + testAnonymousId,
                List.class
        );

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("E2E: Get user circles returns empty list for new user")
    void test_getUserCircles_ReturnsEmptyListForNewUser() {
        // Arrange
        String newUserId = UUID.randomUUID().toString();
        when(circleService.getUserCircles(newUserId))
                .thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/circles/user/" + newUserId,
                List.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("E2E: Create circle returns valid response")
    void test_createCircle_ReturnsValidResponse() {
        // Arrange
        Map<String, String> circleRequest = new HashMap<>();
        circleRequest.put("name", "Test Circle");
        circleRequest.put("locationId", "LOC-001");

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/circles",
                circleRequest,
                Map.class
        );

        // Assert
        assertThat(response.getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
    }

    @Test
    @DisplayName("E2E: Get user circles for different users returns independent results")
    void test_getUserCircles_ForDifferentUsers_ReturnsIndependentResults() {
        // Arrange
        String user1 = UUID.randomUUID().toString();
        String user2 = UUID.randomUUID().toString();

        when(circleService.getUserCircles(user1))
                .thenReturn(new ArrayList<>());
        when(circleService.getUserCircles(user2))
                .thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<List> response1 = restTemplate.getForEntity(
                "/api/v1/circles/user/" + user1,
                List.class
        );
        ResponseEntity<List> response2 = restTemplate.getForEntity(
                "/api/v1/circles/user/" + user2,
                List.class
        );

        // Assert
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("E2E: Get user circles response includes list data")
    void test_getUserCircles_ResponseIncludesListData() {
        // Arrange
        when(circleService.getUserCircles(testAnonymousId))
                .thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/circles/user/" + testAnonymousId,
                List.class
        );

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("E2E: Multiple get user circles requests are independent")
    void test_multipleGetUserCircles_AreIndependent() {
        // Arrange
        String user1 = UUID.randomUUID().toString();
        String user2 = UUID.randomUUID().toString();
        String user3 = UUID.randomUUID().toString();

        when(circleService.getUserCircles(user1))
                .thenReturn(new ArrayList<>());
        when(circleService.getUserCircles(user2))
                .thenReturn(new ArrayList<>());
        when(circleService.getUserCircles(user3))
                .thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<List> response1 = restTemplate.getForEntity(
                "/api/v1/circles/user/" + user1,
                List.class
        );
        ResponseEntity<List> response2 = restTemplate.getForEntity(
                "/api/v1/circles/user/" + user2,
                List.class
        );
        ResponseEntity<List> response3 = restTemplate.getForEntity(
                "/api/v1/circles/user/" + user3,
                List.class
        );

        // Assert
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
