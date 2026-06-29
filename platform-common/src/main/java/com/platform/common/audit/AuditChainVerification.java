package com.platform.common.audit;

public record AuditChainVerification(
        boolean intact,
        long totalChecked,
        Long firstBrokenId,
        String reason
) {
    public static AuditChainVerification intact(long totalChecked) {
        return new AuditChainVerification(true, totalChecked, null, "ok");
    }

    public static AuditChainVerification broken(long totalChecked, Long firstBrokenId, String reason) {
        return new AuditChainVerification(false, totalChecked, firstBrokenId, reason);
    }
}
