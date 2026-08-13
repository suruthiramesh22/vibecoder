package com.prsentry.model;

import java.util.List;

/**
 * Response body for POST /api/review.
 */
public class ReviewResponse {

    private String fileName;
    private int totalFindings;
    private List<FindingDto> findings;

    public ReviewResponse() {
    }

    public ReviewResponse(String fileName, int totalFindings, List<FindingDto> findings) {
        this.fileName = fileName;
        this.totalFindings = totalFindings;
        this.findings = findings;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotalFindings() {
        return totalFindings;
    }

    public void setTotalFindings(int totalFindings) {
        this.totalFindings = totalFindings;
    }

    public List<FindingDto> getFindings() {
        return findings;
    }

    public void setFindings(List<FindingDto> findings) {
        this.findings = findings;
    }
}
