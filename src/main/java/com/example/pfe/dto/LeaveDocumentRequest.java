package com.example.pfe.dto;

import lombok.Data;

@Data
public class LeaveDocumentRequest {
    private String startDate;
    private String endDate;
    private String reason;
    private String signatureBase64;
    private String approvedBy;
    private String approvalDate;
}