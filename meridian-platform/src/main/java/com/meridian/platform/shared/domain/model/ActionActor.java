package com.meridian.platform.shared.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ActionActor(ActionActorType type, UUID userId) {

    public ActionActor {
        Objects.requireNonNull(type, "type must not be null");
        if (type == ActionActorType.USER && userId == null) {
            throw new IllegalArgumentException("USER actor must have a userId");
        }
        if (type == ActionActorType.SYSTEM && userId != null) {
            throw new IllegalArgumentException("SYSTEM actor must not have a userId");
        }
    }

    public static ActionActor user(UUID userId) {
        return new ActionActor(ActionActorType.USER, Objects.requireNonNull(userId, "userId must not be null"));
    }

    public static ActionActor system() {
        return new ActionActor(ActionActorType.SYSTEM, null);
    }
}
