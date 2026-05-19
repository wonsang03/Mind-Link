package com.mindlink.dto;

import java.util.List;

public class DiagnosisSubmitRequest {

    private String testType;
    private List<Integer> answers;

    public String getTestType() { return testType; }
    public List<Integer> getAnswers() { return answers; }
    public void setTestType(String testType) { this.testType = testType; }
    public void setAnswers(List<Integer> answers) { this.answers = answers; }
}
