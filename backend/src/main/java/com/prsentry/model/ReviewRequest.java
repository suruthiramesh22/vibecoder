package com.prsentry.model;

/**
 * Request body for POST /api/review.
 */
public class ReviewRequest {

    private String fileName;
    private String code;

    public ReviewRequest() {
    }

    public ReviewRequest(String fileName, String code) {
        this.fileName = fileName;
        this.code = code;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
