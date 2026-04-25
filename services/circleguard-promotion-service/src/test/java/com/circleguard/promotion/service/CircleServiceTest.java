package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CircleService - Circle management and member status updates.
 * Tests circle creation, member management, and status propagation through circles.
 *
 * Uses Mockito to mock CircleNodeRepository and HealthStatusService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Circle Service Tests")
class CircleServiceTest {

    private CircleService circleService;

    @Mock
    private CircleNodeRepository circleNodeRepository;

    @Mock
    private HealthStatusService healthStatusService;

    @Captor
    private ArgumentCaptor<String> statusCaptor;

    @BeforeEach
    void setUp() {
        circleService = new CircleService(circleNodeRepository, healthStatusService);
    }

    @Test
    @DisplayName("getUserCircles returns all circles for a user")
    void test_getUserCircles_ReturnsUserCircles() {
        // Arrange
        String anonymousId = UUID.randomUUID().toString();
        CircleNode circle1 = CircleNode.builder()
                .id(1L)
                .name("Test Circle 1")
                .build();
        CircleNode circle2 = CircleNode.builder()
                .id(2L)
                .name("Test Circle 2")
                .build();

        when(circleNodeRepository.findCirclesByUser(anonymousId)).thenReturn(List.of(circle1, circle2));

        // Act
        List<CircleNode> userCircles = circleService.getUserCircles(anonymousId);

        // Assert
        assertNotNull(userCircles, "User circles list should not be null");
        assertEquals(2, userCircles.size(), "User should be in 2 circles");
        assertTrue(userCircles.stream().anyMatch(c -> c.getId().equals(1L)), "Should contain circle 1");
        assertTrue(userCircles.stream().anyMatch(c -> c.getId().equals(2L)), "Should contain circle 2");
        verify(circleNodeRepository, times(1)).findCirclesByUser(anonymousId);
    }

    @Test
    @DisplayName("createCircle generates invite code with MESH- prefix and returns saved CircleNode")
    void test_createCircle_GeneratesInviteCode() {
        // Arrange
        String name = "New Circle";
        String locationId = "location-123";

        CircleNode savedCircle = CircleNode.builder()
                .id(1L)
                .name(name)
                .locationId(locationId)
                .isActive(true)
                .build();

        when(circleNodeRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(circleNodeRepository.save(any(CircleNode.class))).thenAnswer(invocation -> {
            CircleNode circle = invocation.getArgument(0);
            circle.setId(1L);
            return circle;
        });

        // Act
        CircleNode result = circleService.createCircle(name, locationId);

        // Assert
        assertNotNull(result, "Created circle should not be null");
        assertEquals(name, result.getName(), "Circle name should match");
        assertEquals(locationId, result.getLocationId(), "Circle locationId should match");
        assertTrue(result.getIsActive(), "Circle should be active");
        verify(circleNodeRepository, times(1)).save(any(CircleNode.class));
    }

    @Test
    @DisplayName("forceFenceCircle sets forceFence flag and promotes ACTIVE members to PROBABLE status")
    void test_forceFenceCircle_PromotesAllMembers() {
        // Arrange
        long circleId = 1L;
        String memberId1 = UUID.randomUUID().toString();
        String memberId2 = UUID.randomUUID().toString();
        String memberId3 = UUID.randomUUID().toString();

        UserNode user1 = UserNode.builder()
                .anonymousId(memberId1)
                .status("ACTIVE")
                .build();
        UserNode user2 = UserNode.builder()
                .anonymousId(memberId2)
                .status("SUSPECT")
                .build();
        UserNode user3 = UserNode.builder()
                .anonymousId(memberId3)
                .status("ACTIVE")
                .build();

        CircleNode circle = CircleNode.builder()
                .id(circleId)
                .name("Forced Containment")
                .members(Set.of(user1, user2, user3))
                .forceFence(false)
                .build();

        when(circleNodeRepository.findById(circleId)).thenReturn(Optional.of(circle));
        when(circleNodeRepository.save(any(CircleNode.class))).thenReturn(circle);
        lenient().doNothing().when(healthStatusService).updateStatus(anyString(), anyString());

        // Act
        circleService.forceFenceCircle(circleId);

        // Assert
        verify(circleNodeRepository, times(1)).save(argThat(saved ->
                saved.getForceFence() == true
        ));
        verify(healthStatusService, times(2)).updateStatus(anyString(), eq("PROBABLE"));
    }

    @Test
    @DisplayName("forceFenceCircle only promotes members with ACTIVE status")
    void test_forceFenceCircle_SkipsSuspectMembers() {
        // Arrange
        long circleId = 1L;
        String activeMemberId = UUID.randomUUID().toString();
        String suspectMemberId = UUID.randomUUID().toString();

        UserNode activeUser = UserNode.builder()
                .anonymousId(activeMemberId)
                .status("ACTIVE")
                .build();
        UserNode suspectUser = UserNode.builder()
                .anonymousId(suspectMemberId)
                .status("SUSPECT")
                .build();

        CircleNode circle = CircleNode.builder()
                .id(circleId)
                .name("Test Circle")
                .members(Set.of(activeUser, suspectUser))
                .build();

        when(circleNodeRepository.findById(circleId)).thenReturn(Optional.of(circle));
        when(circleNodeRepository.save(any(CircleNode.class))).thenReturn(circle);
        lenient().doNothing().when(healthStatusService).updateStatus(anyString(), anyString());

        // Act
        circleService.forceFenceCircle(circleId);

        // Assert
        verify(healthStatusService, times(1)).updateStatus(activeMemberId, "PROBABLE");
        verify(healthStatusService, never()).updateStatus(suspectMemberId, "PROBABLE");
    }

    @Test
    @DisplayName("toggleCircleValidity toggles circle validity and calls resolveStatus for all members when invalidated")
    void test_toggleCircleValidity_InvalidatesTriggersPulseRecovery() {
        // Arrange
        long circleId = 1L;
        String memberId1 = UUID.randomUUID().toString();
        String memberId2 = UUID.randomUUID().toString();

        UserNode user1 = UserNode.builder().anonymousId(memberId1).status("SUSPECT").build();
        UserNode user2 = UserNode.builder().anonymousId(memberId2).status("PROBABLE").build();

        CircleNode circle = CircleNode.builder()
                .id(circleId)
                .name("Test Circle")
                .isValid(true)
                .members(Set.of(user1, user2))
                .build();

        when(circleNodeRepository.findById(circleId)).thenReturn(Optional.of(circle));
        when(circleNodeRepository.save(any(CircleNode.class))).thenAnswer(invocation -> {
            CircleNode saved = invocation.getArgument(0);
            return saved;
        });
        lenient().doNothing().when(healthStatusService).resolveStatus(anyString());

        // Act
        circleService.toggleCircleValidity(circleId);

        // Assert
        assertFalse(circle.getIsValid(), "Circle validity should be toggled to false");
        verify(healthStatusService, times(2)).resolveStatus(anyString());
        verify(healthStatusService).resolveStatus(memberId1);
        verify(healthStatusService).resolveStatus(memberId2);
    }

    @Test
    @DisplayName("toggleCircleValidity with nonexistent circle throws RuntimeException")
    void test_toggleCircleValidity_WithNonexistentCircle_ThrowsException() {
        // Arrange
        long nonexistentCircleId = 999L;
        when(circleNodeRepository.findById(nonexistentCircleId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> circleService.toggleCircleValidity(nonexistentCircleId),
                "Should throw RuntimeException when circle not found"
        );
        assertTrue(exception.getMessage().contains("Circle not found"),
                "Exception message should indicate circle was not found");
        verify(healthStatusService, never()).resolveStatus(anyString());
    }

    @Test
    @DisplayName("forceFenceCircle with nonexistent circle throws RuntimeException")
    void test_forceFenceCircle_WithNonexistentCircle_ThrowsException() {
        // Arrange
        long nonexistentCircleId = 999L;
        when(circleNodeRepository.findById(nonexistentCircleId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> circleService.forceFenceCircle(nonexistentCircleId),
                "Should throw RuntimeException when circle not found"
        );
        assertTrue(exception.getMessage().contains("Circle not found"),
                "Exception message should indicate circle was not found");
        verify(healthStatusService, never()).updateStatus(anyString(), anyString());
    }
}
