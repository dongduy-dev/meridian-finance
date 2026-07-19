package com.meridian.platform.document.domain.model;

import java.util.List;

public record DocumentChecklistReadiness(boolean uploadComplete, boolean processingReady) {

    public static DocumentChecklistReadiness from(List<DocumentChecklistItemState> itemStates) {
        List<DocumentChecklistItemState> states = List.copyOf(itemStates);
        return new DocumentChecklistReadiness(
                states.stream().allMatch(DocumentChecklistItemState::uploadComplete),
                states.stream().allMatch(DocumentChecklistItemState::processingReady)
        );
    }

    public static DocumentChecklistReadiness empty() {
        return new DocumentChecklistReadiness(true, true);
    }
}
