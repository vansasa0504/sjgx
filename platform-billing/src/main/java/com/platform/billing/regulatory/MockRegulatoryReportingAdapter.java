package com.platform.billing.regulatory;

import com.platform.billing.report.GeneratedReport;

public class MockRegulatoryReportingAdapter implements RegulatoryReportingAdapter {
    @Override
    public RegulatorySubmitResult submit(GeneratedReport report) {
        return new RegulatorySubmitResult(true, "REG-" + report.type().name(), "mock submitted");
    }
}