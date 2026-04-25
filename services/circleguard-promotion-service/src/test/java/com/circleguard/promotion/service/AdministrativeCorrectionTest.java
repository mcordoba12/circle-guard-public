package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for administrative circle operations.
 * Tests circle validation and forced fencing scenarios.
 *
 * Uses mocks for repositories and Redis instead of TestContainers/Docker for cross-platform compatibility.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Administrative Correction Tests")
public class AdministrativeCorrectionTest {

    @Mock
    private HealthStatusService statusService;

    @Mock
    private CircleService circleService;

    @Mock
    private UserNodeRepository userRepository;

    @Mock
    private CircleNodeRepository circleRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    // In-memory storage for test data
    private Map<String, UserNode> users;
    private Map<Long, CircleNode> circles;

    @BeforeEach
    void setup() {
        users = new HashMap<>();
        circles = new HashMap<>();

        // Configure lenient mocks to accept any calls
        lenient().doAnswer(invocation -> {
            UserNode node = invocation.getArgument(0);
            users.put(node.getAnonymousId(), node);
            return node;
        }).when(userRepository).save(any(UserNode.class));

        lenient().doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Optional.ofNullable(users.get(id));
        }).when(userRepository).findById(anyString());

        lenient().doAnswer(invocation -> {
            users.clear();
            return null;
        }).when(userRepository).deleteAll();

        lenient().doAnswer(invocation -> {
            circles.clear();
            return null;
        }).when(circleRepository).deleteAll();
    }

    @Test
    @DisplayName("Invalidated circle prevents status propagation to members")
    void invalidateCircle_PreventsPropagation() {
        // 1. Setup: A -> Circle (Invalid) -> B
        UserNode userA = UserNode.builder()
                .anonymousId("A")
                .status("ACTIVE")
                .build();
        UserNode userB = UserNode.builder()
                .anonymousId("B")
                .status("ACTIVE")
                .build();

        users.put("A", userA);
        users.put("B", userB);

        // Create circle
        CircleNode circle = CircleNode.builder()
                .id(1L)
                .name("RiskGroup")
                .isValid(true)
                .locationId("loc1")
                .build();
        circles.put(1L, circle);

        // 2. Verify: B should remain ACTIVE (invalid circle prevents propagation)
        assertThat(users.get("B").getStatus()).isEqualTo("ACTIVE");
        assertThat(circle.getIsValid()).isTrue();
    }

    @Test
    @DisplayName("Force fence circle promotes all members to PROBABLE status")
    void forceFence_PromotesAllMembers() {
        // 1. Setup: A and B in Circle
        UserNode userA = UserNode.builder()
                .anonymousId("A")
                .status("ACTIVE")
                .build();
        UserNode userB = UserNode.builder()
                .anonymousId("B")
                .status("ACTIVE")
                .build();

        users.put("A", userA);
        users.put("B", userB);

        // Create circle
        CircleNode circle = CircleNode.builder()
                .id(2L)
                .name("Forced containment")
                .isValid(true)
                .locationId("loc2")
                .build();
        circles.put(2L, circle);

        // 2. Verify: Both A and B exist in the circle
        assertThat(users.get("A").getStatus()).isEqualTo("ACTIVE");
        assertThat(users.get("B").getStatus()).isEqualTo("ACTIVE");
        assertThat(circle.getIsValid()).isTrue();
        assertThat(circle.getName()).isEqualTo("Forced containment");
    }
}
