package com.example.pfe.Service;

import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class JasperService {

    // ✅ Compiled ONCE at startup — not on every request
    private JasperReport compiledReport;

    @PostConstruct
    public void init() throws Exception {
        log.info("Compiling Jasper leave document template...");
        InputStream template =
                new ClassPathResource("reports/leave_document.jrxml").getInputStream();
        this.compiledReport = JasperCompileManager.compileReport(template);
        log.info("✅ Jasper template compiled successfully.");
    }

    public byte[] generateLeaveDocument(
            String firstName,
            String lastName,
            String department,
            String leaveType,
            String startDate,
            String endDate,
            int daysCount,
            String reason,
            String approvedBy,
            String approvalDate,
            String signatureBase64) throws Exception {

        Map<String, Object> params = new HashMap<>();
        params.put("FIRST_NAME",    firstName);
        params.put("LAST_NAME",     lastName);
        params.put("DEPARTMENT",    department != null ? department : "—");
        params.put("LEAVE_TYPE",    leaveType);
        params.put("START_DATE",    startDate);
        params.put("END_DATE",      endDate);
        params.put("DAYS_COUNT",    daysCount);
        params.put("REASON",        reason);
        params.put("APPROVED_BY",   approvedBy != null ? approvedBy : "—");
        params.put("APPROVAL_DATE", approvalDate != null ? approvalDate : "—");

        // ✅ Signature — graceful fallback if missing
        if (signatureBase64 != null && !signatureBase64.isBlank()) {
            String base64Data = signatureBase64.contains(",")
                    ? signatureBase64.split(",")[1]
                    : signatureBase64;
            byte[] sigBytes = Base64.getDecoder().decode(base64Data);
            params.put("SIGNATURE_IMAGE", new ByteArrayInputStream(sigBytes));
        }

        // ✅ Company logo — warning only if missing, no crash
        try {
            InputStream logo =
                    new ClassPathResource("reports/logo.jpg").getInputStream();
            params.put("COMPANY_LOGO", logo);
        } catch (Exception e) {
            log.warn("Company logo not found at reports/logo.png — skipping.");
        }

        JasperPrint print = JasperFillManager.fillReport(
                compiledReport, params, new JREmptyDataSource()
        );

        return exportToPdf(print);
    }

    // ✅ Compressed PDF export — smaller file size
    private byte[] exportToPdf(JasperPrint jasperPrint) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));

        SimplePdfExporterConfiguration config = new SimplePdfExporterConfiguration();
        config.setCompressed(true);
        exporter.setConfiguration(config);

        exporter.exportReport();
        return out.toByteArray();
    }
}