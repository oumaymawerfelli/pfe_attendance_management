package com.example.pfe.specification;

import com.example.pfe.dto.DocumentFilterRequest;
import com.example.pfe.entities.LeaveDocument;
import com.example.pfe.entities.LeaveDocument.DocumentCategory;
import com.example.pfe.entities.LeaveDocument.DocumentStatus;
import com.example.pfe.entities.LeaveDocument.DocumentType;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Composable JPA Specifications for leave_documents.
 *
 * Uses your exact entity field names:
 *   - User:         firstName, lastName
 *   - LeaveRequest: startDate, endDate
 *   - LeaveDocument: uploadedAt, documentType, documentCategory, status
 */
public class DocumentSpecification {

    /**
     * Admin view — all filters apply.
     */
    public static Specification<LeaveDocument> build(DocumentFilterRequest f) {
        return Specification
                .where(notArchived())
                .and(byUserId(f.getUserId()))
                .and(byUserFullName(f.getUserFullName()))
                .and(byType(f.getDocumentType()))
                .and(byCategory(f.getDocumentCategory()))
                .and(byStatus(f.getStatus()))
                .and(uploadedBetween(f.getUploadedFrom(), f.getUploadedTo()))
                .and(leavePeriodOverlaps(f.getLeaveFrom(), f.getLeaveTo()));
    }

    /**
     * Employee self-view — forces userId, ignores any userId from the filter.
     * This guarantees employees never see documents that aren't theirs.
     */
    public static Specification<LeaveDocument> forEmployee(Long userId,
                                                           DocumentFilterRequest f) {
        return Specification
                .where(notArchived())
                .and(byUserId(userId))                           // always forced
                .and(byType(f.getDocumentType()))
                .and(byCategory(f.getDocumentCategory()))
                .and(byStatus(f.getStatus()))
                .and(uploadedBetween(f.getUploadedFrom(), f.getUploadedTo()))
                .and(leavePeriodOverlaps(f.getLeaveFrom(), f.getLeaveTo()));
    }

    // ─── Predicates ───────────────────────────────────────────

    private static Specification<LeaveDocument> notArchived() {
        return (root, q, cb) ->
                cb.notEqual(root.get("status"), DocumentStatus.ARCHIVED);
    }

    private static Specification<LeaveDocument> byUserId(Long id) {
        return (root, q, cb) ->
                id == null ? null : cb.equal(root.get("user").get("id"), id);
    }

    /**
     * Partial case-insensitive match on User.firstName OR User.lastName.
     * Matches your mapper: firstName + " " + lastName.
     */
    private static Specification<LeaveDocument> byUserFullName(String name) {
        return (root, q, cb) -> {
            if (name == null || name.isBlank()) return null;
            String pattern = "%" + name.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("user").get("firstName")), pattern),
                    cb.like(cb.lower(root.get("user").get("lastName")),  pattern)
            );
        };
    }

    private static Specification<LeaveDocument> byType(DocumentType type) {
        return (root, q, cb) ->
                type == null ? null : cb.equal(root.get("documentType"), type);
    }

    private static Specification<LeaveDocument> byCategory(DocumentCategory category) {
        return (root, q, cb) ->
                category == null ? null : cb.equal(root.get("documentCategory"), category);
    }

    private static Specification<LeaveDocument> byStatus(DocumentStatus status) {
        return (root, q, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    private static Specification<LeaveDocument> uploadedBetween(LocalDate from, LocalDate to) {
        return (root, q, cb) -> {
            if (from == null && to == null) return null;
            var field = root.<java.time.LocalDateTime>get("uploadedAt");
            if (from == null) return cb.lessThanOrEqualTo(field, to.atTime(23, 59, 59));
            if (to   == null) return cb.greaterThanOrEqualTo(field, from.atStartOfDay());
            return cb.between(field, from.atStartOfDay(), to.atTime(23, 59, 59));
        };
    }

    /**
     * Filters by leave request period overlap.
     * Uses LeaveRequest.startDate and LeaveRequest.endDate — your exact field names.
     */
    private static Specification<LeaveDocument> leavePeriodOverlaps(LocalDate from,
                                                                    LocalDate to) {
        return (root, q, cb) -> {
            if (from == null && to == null) return null;
            var req = root.join("leaveRequest", JoinType.LEFT);
            if (from == null)
                return cb.lessThanOrEqualTo(req.get("startDate"), to);
            if (to == null)
                return cb.greaterThanOrEqualTo(req.get("endDate"), from);
            return cb.and(
                    cb.lessThanOrEqualTo(req.get("startDate"), to),
                    cb.greaterThanOrEqualTo(req.get("endDate"), from)
            );
        };
    }
}