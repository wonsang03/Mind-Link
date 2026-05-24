package com.mindlink.dto;

import com.mindlink.domain.Report;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReportRequest {

    @NotNull(message = "신고 대상 유형을 지정해주세요.")
    private Report.TargetType targetType; // POST 또는 COMMENT

    @NotNull(message = "신고 대상 ID를 지정해주세요.")
    private Long targetId;

    @NotBlank(message = "신고 사유를 입력해주세요.")
    private String reason;

    public Report.TargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public void setTargetType(Report.TargetType targetType) { this.targetType = targetType; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public void setReason(String reason) { this.reason = reason; }
}
