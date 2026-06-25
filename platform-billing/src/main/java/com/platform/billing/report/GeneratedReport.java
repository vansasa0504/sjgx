package com.platform.billing.report;

import java.nio.file.Path;

public record GeneratedReport(ReportType type, Path path) {
}