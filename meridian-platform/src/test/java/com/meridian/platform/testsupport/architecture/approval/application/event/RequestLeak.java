package com.meridian.platform.testsupport.architecture.approval.application.event;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import java.util.List;

public record RequestLeak(List<CorrectionPlanRequest> plans) {
}
