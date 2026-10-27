package com.meridian.platform.approval.application.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CorrectionPlanFacts(List<CorrectionTaskFacts> tasks) {
    public CorrectionPlanFacts {
        tasks = tasks == null ? null : Collections.unmodifiableList(new ArrayList<>(tasks));
    }
}
