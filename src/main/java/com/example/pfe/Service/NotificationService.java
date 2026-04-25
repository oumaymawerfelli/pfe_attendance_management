package com.example.pfe.Service;

import com.example.pfe.Repository.NotificationRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.NotificationDTO;
import com.example.pfe.entities.Notification;
import com.example.pfe.entities.User;
import com.example.pfe.enums.NotificationType;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository         userRepository;
    private final SseEmitterService      sseEmitterService;

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC API — called by other services
    // ══════════════════════════════════════════════════════════════════════════

    /** First login after account activation */
    @Transactional
    public void notifyWelcome(Long userId) {
        User user = getUser(userId);
        send(user, NotificationType.WELCOME,
                "Welcome, " + user.getFirstName() + "! 👋",
                "Your account is active. Explore your dashboard to get started.",
                "/dashboard");
    }

    /** Employee checked in after the grace period */
    @Transactional
    public void notifyLateArrival(Long userId, String checkinTime) {
        User user = getUser(userId);
        send(user, NotificationType.LATE_ARRIVAL,
                "Late Arrival Recorded",
                "Your check-in at " + checkinTime + " was recorded as late. Please be on time tomorrow.",
                "/attendance/summary");
    }

    /** No checkout recorded for the previous working day */
    @Transactional
    public void notifyMissedCheckout(Long userId, String date) {
        User user = getUser(userId);
        send(user, NotificationType.MISSED_CHECKOUT,
                "Missed Checkout",
                "No checkout was recorded for " + date + ". Please fix it from your attendance dashboard.",
                "/attendance/summary");
    }

    /** Checked out before the end-of-day threshold */
    @Transactional
    public void notifyEarlyDeparture(Long userId, String checkoutTime) {
        User user = getUser(userId);
        send(user, NotificationType.EARLY_DEPARTURE,
                "Early Departure Recorded",
                "You checked out at " + checkoutTime + ", which is before the standard end of day.",
                "/attendance/summary");
    }

    /** Leave request approved */
    @Transactional
    public void notifyLeaveApproved(Long userId, String leaveType, String startDate, String endDate) {
        User user = getUser(userId);
        send(user, NotificationType.LEAVE_APPROVED,
                "Leave Request Approved ✓",
                "Your " + leaveType + " leave from " + startDate + " to " + endDate + " has been approved.",
                "/leave/my");
    }

    /** Leave request rejected */
    @Transactional
    public void notifyLeaveRejected(Long userId, String leaveType, String reason) {
        User user = getUser(userId);
        send(user, NotificationType.LEAVE_REJECTED,
                "Leave Request Rejected",
                "Your " + leaveType + " leave request was rejected." +
                        (reason != null && !reason.isBlank() ? " Reason: " + reason : ""),
                "/leave/my");
    }

    /** Employee assigned to a project */
    @Transactional
    public void notifyProjectAssigned(Long userId, String projectName) {
        User user = getUser(userId);
        send(user, NotificationType.PROJECT_ASSIGNED,
                "Assigned to Project",
                "You have been assigned to project \"" + projectName + "\".",
                "/projects");
    }

    /** Employee assigned to a project manager */
    @Transactional
    public void notifyPmAssigned(Long userId, String pmFullName, String projectName) {
        User user = getUser(userId);
        send(user, NotificationType.PM_ASSIGNED,
                "Project Manager Assigned",
                pmFullName + " is now your project manager on \"" + projectName + "\".",
                "/projects");
    }

    /** A project the employee works on was updated */
    @Transactional
    public void notifyProjectUpdated(Long userId, String projectName) {
        User user = getUser(userId);
        send(user, NotificationType.PROJECT_UPDATED,
                "Project Updated",
                "Project \"" + projectName + "\" has been updated. Check the latest details.",
                "/projects");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONTROLLER-FACING METHODS
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getForUser(Long userId, Pageable pageable) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        notificationRepository.markReadById(notificationId, userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void send(User user, NotificationType type,
                      String title, String message, String link) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .build();

        notificationRepository.save(notification);

        // Push to browser immediately if user is online
        sseEmitterService.push(user.getId(), toDTO(notification));

        log.debug("Notification [{}] sent to user {}", type, user.getId());
    }

    private NotificationDTO toDTO(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .link(n.getLink())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));
    }
}