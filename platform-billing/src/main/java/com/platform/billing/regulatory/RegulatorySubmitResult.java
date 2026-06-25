package com.platform.billing.regulatory;

public record RegulatorySubmitResult(boolean success, String receiptNo, String message) {
}