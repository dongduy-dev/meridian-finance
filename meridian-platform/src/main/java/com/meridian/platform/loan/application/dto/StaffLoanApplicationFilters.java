package com.meridian.platform.loan.application.dto;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

public record StaffLoanApplicationFilters(
        ProductCode productCode,
        LoanApplicationStatus status,
        String applicationNumber,
        OriginationChannel originationChannel,
        LocalDate submittedFrom,
        LocalDate submittedTo
) {
    // Staff timestamps are displayed in this zone; submission clocks store UTC local timestamps.
    private static final ZoneId STAFF_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public StaffLoanApplicationFilters {
        if (applicationNumber != null) {
            applicationNumber = applicationNumber.trim().toUpperCase(Locale.ROOT);
            if (applicationNumber.isEmpty()) {
                applicationNumber = null;
            } else if (applicationNumber.length() > 50
                    || !applicationNumber.matches("(?:SA|UCL|CL)-[0-9]{8}-[0-9]{6,}")) {
                throw new IllegalArgumentException("Enter a complete application number of at most 50 characters.");
            }
        }
        requireSupportedDate(submittedFrom);
        requireSupportedDate(submittedTo);
        if (submittedFrom != null && submittedTo != null && submittedFrom.isAfter(submittedTo)) {
            throw new IllegalArgumentException("Submitted from must be on or before Submitted to.");
        }
    }

    public LocalDateTime submittedFromInclusive() {
        return submittedFrom == null ? null : storedTimestamp(submittedFrom);
    }

    public LocalDateTime submittedToExclusive() {
        return submittedTo == null ? null : storedTimestamp(submittedTo.plusDays(1));
    }

    private static LocalDateTime storedTimestamp(LocalDate date) {
        return date.atStartOfDay(STAFF_TIME_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static void requireSupportedDate(LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw new IllegalArgumentException("Submission dates must use YYYY-MM-DD with a year from 0001 to 9999.");
        }
    }
}
