package com.platform.billing.regulatory;

import com.platform.billing.report.GeneratedReport;

public interface RegulatoryReportingAdapter {
    RegulatorySubmitResult submit(GeneratedReport report);
}