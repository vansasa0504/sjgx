package com.platform.billing.finance;

public record FinanceSyncResult(boolean success, String externalNo, String message) {
}