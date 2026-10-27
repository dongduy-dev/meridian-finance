package com.meridian.platform.testsupport;

import com.meridian.platform.approval.application.dto.CorrectionDocumentType;
import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class CorrectionPolicyCompatibilityCases {
    private static final UUID ITEM = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VERSION = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private CorrectionPolicyCompatibilityCases() {
    }

    public record Case(String name, CorrectionPlanRequest plan, String code, String message) {
        @Override public String toString() { return name; }
    }

    public static Stream<Case> cases() {
        List<Case> cases = new ArrayList<>();
        String count = "A correction plan must contain between 1 and 10 tasks.";
        cases.add(invalid("missing plan", null, count));
        cases.add(invalid("missing list", new CorrectionPlanRequest(null), count));
        cases.add(invalid("empty list", new CorrectionPlanRequest(List.of()), count));
        cases.add(invalid("eleven tasks", new CorrectionPlanRequest(Collections.nCopies(11, upload())), count));
        cases.add(invalid("null task", plan((CorrectionTaskRequest) null), "Correction task scope and responsibility are required."));
        cases.add(invalid("null scope", plan(task(null, CorrectionResponsibility.CUSTOMER, "Upload", null)), "Correction task scope and responsibility are required."));
        cases.add(invalid("null responsibility", plan(task(CorrectionScope.APPLICATION_TERMS, null, null, null)), "Correction task scope and responsibility are required."));
        cases.add(valid("customer upload", plan(upload())));
        cases.add(valid("staff upload", plan(task(CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD, CorrectionResponsibility.STAFF, null, "Upload"))));
        cases.add(valid("replacement", plan(task(CorrectionScope.DOCUMENT_REPLACEMENT, CorrectionResponsibility.CUSTOMER, "Replace", null))));
        cases.add(valid("review", plan(task(CorrectionScope.DOCUMENT_REVIEW, CorrectionResponsibility.STAFF, null, "Review"))));
        for (String text : List.of("x", "x".repeat(499), "x".repeat(500), "  x  ", "  " + "x".repeat(500) + "  ", "line\nsecond\tcolumn\rthird")) {
            cases.add(valid("valid instruction length/content " + text.length(), plan(task(CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD, CorrectionResponsibility.CUSTOMER, text, null))));
        }
        for (String text : List.of("", " \t\r\n ")) {
            cases.add(invalid("blank customer instruction", plan(task(CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD, CorrectionResponsibility.CUSTOMER, text, null)), "Customer tasks require only a customer instruction."));
        }
        cases.add(invalid("wrong customer instruction side", plan(task(CorrectionScope.APPLICATION_TERMS, CorrectionResponsibility.CUSTOMER, null, "staff")), "Customer tasks require only a customer instruction."));
        cases.add(invalid("both instruction sides", plan(task(CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD, CorrectionResponsibility.CUSTOMER, "customer", "staff")), "Customer tasks require only a customer instruction."));
        cases.add(invalid("wrong staff instruction side", plan(task(CorrectionScope.DOCUMENT_REVIEW, CorrectionResponsibility.STAFF, "customer", null)), "Staff tasks require only a staff instruction."));
        cases.add(invalid("missing staff instruction", plan(task(CorrectionScope.DOCUMENT_REVIEW, CorrectionResponsibility.STAFF, null, "  ")), "Staff tasks require only a staff instruction."));
        for (String text : List.of("x".repeat(501), "x" + (char) 0 + "y", "x" + (char) 127 + "y")) {
            cases.add(invalid("instruction content before terms", plan(task(CorrectionScope.APPLICATION_TERMS, CorrectionResponsibility.CUSTOMER, text, null)), "Correction instructions must be plain text between 1 and 500 characters."));
        }
        cases.add(new Case("terms after instruction validation", plan(task(CorrectionScope.APPLICATION_TERMS, CorrectionResponsibility.CUSTOMER, "Change terms", null)), "CORRECTION_FIELD_NOT_ALLOWED", "Requested amount and term are not editable during this checkpoint."));
        cases.add(invalid("duplicate creation before tuple", plan(upload(), upload()), "A correction plan cannot create the same checklist item more than once."));
        var replacement = task(CorrectionScope.DOCUMENT_REPLACEMENT, CorrectionResponsibility.CUSTOMER, "Replace", null);
        cases.add(invalid("duplicate tuple", plan(replacement, replacement), "Duplicate correction tasks are not allowed."));
        for (int field = 0; field < 4; field++) {
            cases.add(invalid("upload field " + field, plan(new CorrectionTaskRequest(CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                    CorrectionResponsibility.CUSTOMER, field == 0 ? null : CorrectionDocumentType.RECENT_PAYSLIP,
                    field != 1, field == 2 ? ITEM : null, field == 3 ? VERSION : null, "Upload", null)), "Supporting document upload task fields are invalid."));
        }
        for (CorrectionScope scope : List.of(CorrectionScope.DOCUMENT_REPLACEMENT, CorrectionScope.DOCUMENT_REVIEW)) {
            boolean review = scope == CorrectionScope.DOCUMENT_REVIEW;
            for (int field = 0; field < 5; field++) {
                var party = field == 0 ? (review ? CorrectionResponsibility.CUSTOMER : CorrectionResponsibility.STAFF)
                        : (review ? CorrectionResponsibility.STAFF : CorrectionResponsibility.CUSTOMER);
                cases.add(invalid(scope + " field " + field, plan(new CorrectionTaskRequest(scope, party,
                        field == 1 ? null : CorrectionDocumentType.RECENT_PAYSLIP, field == 2,
                        field == 3 ? null : ITEM, field == 4 ? null : VERSION,
                        party == CorrectionResponsibility.CUSTOMER ? "Act" : null,
                        party == CorrectionResponsibility.STAFF ? "Act" : null)),
                        review ? "Document review task fields are invalid." : "Document replacement task fields are invalid."));
            }
        }
        var ten = new ArrayList<CorrectionTaskRequest>();
        for (int i = 0; i < 10; i++) ten.add(new CorrectionTaskRequest(CorrectionScope.DOCUMENT_REVIEW,
                CorrectionResponsibility.STAFF, CorrectionDocumentType.INCOME_PROOF, false, UUID.randomUUID(), VERSION, null, "Review"));
        cases.add(valid("ten distinct tasks", new CorrectionPlanRequest(ten)));
        return cases.stream();
    }

    private static Case invalid(String name, CorrectionPlanRequest plan, String message) {
        return new Case(name, plan, "INVALID_CORRECTION_PLAN", message);
    }
    private static Case valid(String name, CorrectionPlanRequest plan) { return new Case(name, plan, null, null); }
    private static CorrectionPlanRequest plan(CorrectionTaskRequest... tasks) { return new CorrectionPlanRequest(Arrays.asList(tasks)); }
    private static CorrectionTaskRequest upload() { return task(CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD, CorrectionResponsibility.CUSTOMER, "Upload", null); }
    private static CorrectionTaskRequest task(CorrectionScope scope, CorrectionResponsibility party, String customer, String staff) {
        boolean existing = scope == CorrectionScope.DOCUMENT_REPLACEMENT || scope == CorrectionScope.DOCUMENT_REVIEW;
        return new CorrectionTaskRequest(scope, party, CorrectionDocumentType.RECENT_PAYSLIP, !existing,
                existing ? ITEM : null, existing ? VERSION : null, customer, staff);
    }
}
