package com.example.pfe.Repository;

import com.example.pfe.entities.LeaveDocument;
import com.example.pfe.entities.LeaveDocument.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveDocumentRepository
        extends JpaRepository<LeaveDocument, Long>,
        JpaSpecificationExecutor<LeaveDocument> {

    // All documents for a leave request (used to show attachments on a request card)
    List<LeaveDocument> findByLeaveRequestIdOrderByCreatedAtDesc(Long leaveRequestId);

    // All documents for an employee — employee self-view
    List<LeaveDocument> findByUserIdAndStatusNotOrderByCreatedAtDesc(
            Long userId, DocumentStatus excludedStatus);

    // Check if an acceptance letter was already generated for a request
    boolean existsByLeaveRequestIdAndDocumentType(
            Long leaveRequestId,
            LeaveDocument.DocumentType type);
}