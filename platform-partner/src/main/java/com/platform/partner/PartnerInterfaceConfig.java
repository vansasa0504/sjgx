package com.platform.partner;

public record PartnerInterfaceConfig(long partnerId, String protocol, String endpoint, String encryptedCredential) {
}
