package com.prsentry.service;

import com.prsentry.model.Finding;
import com.prsentry.model.FindingDto;
import com.prsentry.model.ReviewResponse;
import com.prsentry.repository.FindingRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeReviewService {

    private final FindingRepository findingRepository;

    // Pattern: var x = Something();  or  Type x = Something();
    private static final Pattern ASSIGNMENT_FROM_METHOD = Pattern.compile(
            "(?:var|[A-Za-z][\\w<>\\[\\]]*(?:\\?)?)\\s+(\\w+)\\s*=\\s*\\w+(?:\\.\\w+)*\\(.*\\)\\s*;");

    // Keywords grouped by responsibility area (for basic SRP detection)
    private static final String[] DB_KEYWORDS = {
            "SqlConnection", "DbContext", "ExecuteQuery", "ExecuteNonQuery",
            "SqlCommand", "INSERT INTO", "UPDATE ", "DELETE FROM",
            "SaveChanges", "OpenConnection", "ExecuteReader"
    };
    private static final String[] EMAIL_KEYWORDS = {
            "SmtpClient", "SendEmail", "MailMessage", "SendMail", "EmailAddress"
    };
    private static final String[] REPORT_KEYWORDS = {
            "GenerateReport", "PrintReport", "ExportReport", "CreateReport"
    };
    private static final String[] USER_KEYWORDS = {
            "CreateUser", "RegisterUser", "DeleteUser", "UpdateUser"
    };

    public CodeReviewService(FindingRepository findingRepository) {
        this.findingRepository = findingRepository;
    }

    /**
     * Analyze the provided C# code and return detected findings.
     */
    public ReviewResponse analyze(String fileName, String code) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = code.split("\n");

        checkAsyncVoid(fileName, lines, findings);
        checkWaitBlocking(fileName, lines, findings);
        checkResultBlocking(fileName, lines, findings);
        checkNullHandling(fileName, lines, findings);
        checkSolidViolation(fileName, lines, findings);

        // Persist every finding to PostgreSQL
        findingRepository.saveAll(findings);

        // Map entities to DTOs (response excludes id and fileName)
        List<FindingDto> dtos = findings.stream()
                .map(f -> new FindingDto(f.getLineNumber(), f.getCategory(), f.getSeverity(), f.getMessage()))
                .toList();

        return new ReviewResponse(fileName, dtos.size(), dtos);
    }

    // ========================================================================
    // RULE 1 — async void
    // ========================================================================

    private void checkAsyncVoid(String fileName, String[] lines, List<Finding> findings) {
        Pattern pattern = Pattern.compile("\\basync\\s+void\\b");
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i]).find()) {
                findings.add(new Finding(
                        fileName, i + 1, "ASYNC", "WARNING",
                        "Avoid async void. Prefer async Task."));
            }
        }
    }

    // ========================================================================
    // RULE 2 — .Wait()
    // ========================================================================

    private void checkWaitBlocking(String fileName, String[] lines, List<Finding> findings) {
        Pattern pattern = Pattern.compile("\\.Wait\\(\\)");
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i]).find()) {
                findings.add(new Finding(
                        fileName, i + 1, "ASYNC", "WARNING",
                        "Avoid blocking asynchronous code with .Wait(). Prefer await."));
            }
        }
    }

    // ========================================================================
    // RULE 3 — .Result
    // ========================================================================

    private void checkResultBlocking(String fileName, String[] lines, List<Finding> findings) {
        // Match .Result but NOT .ResultSet, .Results, etc.
        Pattern pattern = Pattern.compile("\\.Result\\b(?!s|S)");
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i]).find()) {
                findings.add(new Finding(
                        fileName, i + 1, "ASYNC", "WARNING",
                        "Avoid blocking asynchronous code with .Result. Prefer await."));
            }
        }
    }

    // ========================================================================
    // RULE 4 — Null handling
    // ========================================================================

    private void checkNullHandling(String fileName, String[] lines, List<Finding> findings) {
        // Track variables assigned from method calls
        Map<String, Integer> varsFromMethods = new LinkedHashMap<>();
        Set<String> nullCheckedVars = new HashSet<>();
        Set<String> alreadyReported = new HashSet<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }

            // Detect null checks — remove from tracking
            for (String varName : varsFromMethods.keySet()) {
                if (line.contains(varName + " != null") ||
                    line.contains(varName + " == null") ||
                    line.contains(varName + " is null") ||
                    line.contains(varName + " is not null") ||
                    line.contains(varName + "?.") ||
                    line.contains(varName + " ?? ")) {
                    nullCheckedVars.add(varName);
                }
            }

            // Detect variable assignment from a method call
            Matcher matcher = ASSIGNMENT_FROM_METHOD.matcher(line);
            if (matcher.find()) {
                String varName = matcher.group(1);
                varsFromMethods.put(varName, i);
            }

            // Check for member access on tracked variables (potential null deref)
            List<String> toReport = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : varsFromMethods.entrySet()) {
                String varName = entry.getKey();
                int assignedLine = entry.getValue();

                if (alreadyReported.contains(varName) || nullCheckedVars.contains(varName)) {
                    continue;
                }
                if (i <= assignedLine) {
                    continue;
                }

                // Check if this line accesses a member on the variable
                String memberAccess = varName + ".";
                String nullConditional = varName + "?.";
                if (line.contains(memberAccess) && !line.contains(nullConditional)) {
                    toReport.add(varName);
                }
            }

            // Report findings (outside iteration to avoid ConcurrentModificationException)
            for (String varName : toReport) {
                findings.add(new Finding(
                        fileName, i + 1, "NULL_HANDLING", "WARNING",
                        "Potential null dereference on '" + varName + "'. Add appropriate null handling."));
                alreadyReported.add(varName);
            }
        }
    }

    // ========================================================================
    // RULE 5 — Basic Single Responsibility Principle (SRP)
    // ========================================================================

    private void checkSolidViolation(String fileName, String[] lines, List<Finding> findings) {
        String fullCode = String.join("\n", lines);

        // Only apply to code that contains a class definition
        if (!fullCode.contains("class ")) {
            return;
        }

        // Find the line number of the class declaration
        int classLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("class ")) {
                classLine = i + 1;
                break;
            }
        }
        if (classLine < 0) {
            return;
        }

        // Count how many distinct responsibility areas appear
        Set<String> responsibilities = new HashSet<>();

        if (containsAny(fullCode, DB_KEYWORDS)) {
            responsibilities.add("DATABASE");
        }
        if (containsAny(fullCode, EMAIL_KEYWORDS)) {
            responsibilities.add("EMAIL");
        }
        if (containsAny(fullCode, REPORT_KEYWORDS)) {
            responsibilities.add("REPORT");
        }
        if (containsAny(fullCode, USER_KEYWORDS)) {
            responsibilities.add("USER_MANAGEMENT");
        }

        if (responsibilities.size() >= 2) {
            findings.add(new Finding(
                    fileName, classLine, "SOLID", "WARNING",
                    "This class appears to have multiple responsibilities. Consider separating unrelated responsibilities."));
        }
    }

    private boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
