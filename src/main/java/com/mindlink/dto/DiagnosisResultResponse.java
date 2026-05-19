package com.mindlink.dto;

public class DiagnosisResultResponse {

    private String testType;
    private int score;
    private String level;
    private String message;
    private boolean highRisk;

    public DiagnosisResultResponse(String testType, int score, String level, String message, boolean highRisk) {
        this.testType = testType;
        this.score = score;
        this.level = level;
        this.message = message;
        this.highRisk = highRisk;
    }

    public String getTestType() { return testType; }
    public int getScore() { return score; }
    public String getLevel() { return level; }
    public String getMessage() { return message; }
    public boolean isHighRisk() { return highRisk; }
}
