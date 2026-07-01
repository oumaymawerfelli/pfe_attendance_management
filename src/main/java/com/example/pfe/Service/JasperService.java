package com.example.pfe.Service;

import com.example.pfe.entities.LeaveRequest;
import com.example.pfe.entities.User;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class JasperService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd / MM / yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ✅ Compiled ONCE at startup
    private JasperReport compiledReport;
    private JasperReport exitAuthorizationReport;

    // ─── Update your @PostConstruct init() to compile BOTH reports ───
    @PostConstruct
    public void init() throws Exception {
        log.info("Compiling Jasper templates...");

        try (InputStream demande = new ClassPathResource("reports/demande_conge.jrxml").getInputStream()) {
            this.compiledReport = JasperCompileManager.compileReport(demande);
        }
        try (InputStream sortie = new ClassPathResource("reports/autorisation_sortie.jrxml").getInputStream()) {
            this.exitAuthorizationReport = JasperCompileManager.compileReport(sortie);
        }

        log.info("✅ Both Jasper templates compiled successfully.");
    }

    /**
     * Generate the ArabSoft "Autorisation de Sortie" PDF.
     *
     * @param leave            the approved exit request (LeaveType = EXIT_AUTHORIZATION)
     * @param signatureBase64  DG or Project Manager signature, base64 (may be null)
     * @param monthlyCount     number of exit authorizations this employee already has this month
     */
    public byte[] generateAutorisationSortie(LeaveRequest leave,
                                             String signatureBase64,
                                             int monthlyCount) throws Exception {

        User user = leave.getUser();
        Map<String, Object> params = new HashMap<>();

        // ─── Identity ───
        params.put("FIRST_NAME", nz(user.getFirstName()));
        params.put("LAST_NAME",  nz(user.getLastName()));
        params.put("PHONE",      nz(user.getPhone()));
        params.put("ADDRESS",    nz(user.getAddress()));

        // ─── Exit details ───
        params.put("EXIT_DATE",     leave.getStartDate().format(DATE_FMT));
        params.put("EXIT_TIME",     leave.getExitTime()    != null ? leave.getExitTime().format(TIME_FMT)   : "");
        params.put("RETURN_TIME",   leave.getReturnTime()  != null ? leave.getReturnTime().format(TIME_FMT) : "");
        params.put("DURATION",      computeDurationLabel(leave.getExitTime(), leave.getReturnTime()));
        params.put("MOTIF",         nz(leave.getReason()));
        params.put("MONTHLY_COUNT", String.valueOf(monthlyCount));

        // ─── Images ───
        loadImage(params, "COMPANY_LOGO", "static/images/arabsoft_logo.png");
        loadImage(params, "CERT_LOGO",    "static/images/tuv_cert.png");

        // ─── Signature ───
        if (signatureBase64 != null && !signatureBase64.isBlank()) {
            String clean = signatureBase64.contains(",")
                    ? signatureBase64.substring(signatureBase64.indexOf(',') + 1)
                    : signatureBase64;
            byte[] sig = Base64.getDecoder().decode(clean);
            params.put("DG_SIGNATURE", new ByteArrayInputStream(sig));
        }

        JasperPrint print = JasperFillManager.fillReport(
                exitAuthorizationReport, params, new JREmptyDataSource());
        return exportToPdf(print);
    }
    /** Returns a human label like "1h 15min" from two LocalTime values. */
    private String computeDurationLabel(LocalTime from, LocalTime to) {
        if (from == null || to == null) return "";
        long minutes = ChronoUnit.MINUTES.between(from, to);
        if (minutes <= 0) return "";
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) return m + " min";
        if (m == 0) return h + "h";
        return h + "h " + m + "min";
    }
    public byte[] generateDemandeConge(LeaveRequest leave,
                                       String signatureBase64,
                                       String leaveBalanceText) throws Exception {

        User user = leave.getUser();
        String typeName = leave.getLeaveType() != null ? leave.getLeaveType().name() : "";

        Map<String, Object> params = new HashMap<>();

        // ─── Identity (from User entity) ───
        params.put("FIRST_NAME", nz(user.getFirstName()));
        params.put("LAST_NAME",  nz(user.getLastName()));
        params.put("MATRICULE",  nz(user.getUsername()));
        params.put("PHONE",      nz(user.getPhone()));
        params.put("ADDRESS",    nz(user.getAddress()));
        params.put("EQUIPE",     user.getDepartment() != null ? user.getDepartment().name() : "");

        // ─── Dates ───
        params.put("REQUEST_DATE", LocalDate.now().format(DATE_FMT));
        params.put("REQUEST_TIME", LocalTime.now().format(TIME_FMT));
        params.put("START_DATE",   leave.getStartDate().format(DATE_FMT));
        params.put("RETURN_DATE",  leave.getEndDate().plusDays(1).format(DATE_FMT));

        // ─── Details ───
        int days = leave.getDaysCount() != null ? leave.getDaysCount().intValue() : 0;
        params.put("DURATION_DAYS", String.valueOf(days));
        params.put("LEAVE_BALANCE", leaveBalanceText != null && !leaveBalanceText.isBlank()
                ? leaveBalanceText : "—");
        params.put("OBSERVATIONS",  nz(leave.getReason()));

        // ─── Nature de congé — map LeaveType to 3 categories ───
        boolean isAnnuel = typeName.contains("ANNUAL") || typeName.contains("ANNUEL");
        boolean isRecup  = typeName.contains("RECUP");
        boolean isExc    = !isAnnuel && !isRecup;

        params.put("IS_ANNUEL",       String.valueOf(isAnnuel));
        params.put("IS_RECUPERATION", String.valueOf(isRecup));
        params.put("IS_EXCEPTIONNEL", String.valueOf(isExc));
        params.put("ANNUEL_DETAIL",   isAnnuel ? days + " jours ouvrés" : "");

        // ─── Exceptional sub-types — only fill the matching one ───
        String dateStr = leave.getStartDate().format(DATE_FMT);
        params.put("EXC_MARIAGE",      typeName.contains("MARRIAGE")     || typeName.contains("MARIAGE")    ? dateStr : "");
        params.put("EXC_NAISSANCE",    typeName.contains("BIRTH")        || typeName.contains("NAISSANCE")  ? dateStr : "");
        params.put("EXC_CIRCONCISION", typeName.contains("CIRCONCISION")                                    ? dateStr : "");
        params.put("EXC_DECES",        typeName.contains("DEATH")        || typeName.contains("DECES")     ? dateStr : "");

        // ─── Récupération dates (empty unless tracked separately) ───
        params.put("REC_DATE_1", ""); params.put("REC_DATE_2", "");
        params.put("REC_DATE_3", ""); params.put("REC_DATE_4", "");

        // ─── Images (logo + TÜV cert) ───
        loadImage(params, "COMPANY_LOGO", "static/images/arabsoft_logo.png");
        loadImage(params, "CERT_LOGO",    "static/images/tuv_cert.png");

        // ─── DG signature ───
        if (signatureBase64 != null && !signatureBase64.isBlank()) {
            String clean = signatureBase64.contains(",")
                    ? signatureBase64.substring(signatureBase64.indexOf(',') + 1)
                    : signatureBase64;
            byte[] sig = Base64.getDecoder().decode(clean);
            params.put("DG_SIGNATURE", new ByteArrayInputStream(sig));
        }

        JasperPrint print = JasperFillManager.fillReport(
                compiledReport, params, new JREmptyDataSource());
        return exportToPdf(print);
    }

    // ─── Helpers ──────────────────────────────────────────────

    private void loadImage(Map<String, Object> params, String paramName, String path) {
        try {
            params.put(paramName, new ClassPathResource(path).getInputStream());
        } catch (Exception e) {
            log.warn("Image not found at {} — skipping.", path);
        }
    }

    private String nz(String s) { return s == null ? "" : s; }

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