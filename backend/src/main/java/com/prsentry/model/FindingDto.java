package com.prsentry.model;

/**
 * A single finding in the review response (excludes id and fileName).
 */
public class FindingDto {

    private int lineNumber;
    private String category;
    private String severity;
    private String message;

    public FindingDto() {
    }

    public FindingDto(int lineNumber, String category, String severity, String message) {
        this.lineNumber = lineNumber;
        this.category = category;
        this.severity = severity;
        this.message = message;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
