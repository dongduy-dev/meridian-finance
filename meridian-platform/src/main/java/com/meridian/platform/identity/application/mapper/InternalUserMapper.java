package com.meridian.platform.identity.application.mapper;

import com.meridian.platform.identity.application.dto.AssignableInternalRoleDto;
import com.meridian.platform.identity.application.dto.InternalUserDto;
import com.meridian.platform.identity.application.port.out.AssignableInternalRole;
import com.meridian.platform.identity.application.port.out.InternalUserRecord;
import com.meridian.platform.identity.domain.model.User;
import com.meridian.platform.identity.domain.model.UserStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class InternalUserMapper {

    public InternalUserDto toDto(InternalUserRecord user) {
        return new InternalUserDto(
                user.userId(), user.email(), user.displayName(), user.status().name(), sorted(user.assignedRoleCodes())
        );
    }

    public InternalUserDto toDto(User user, UserStatus status, Set<String> assignedRoleCodes) {
        return new InternalUserDto(user.id(), user.email(), user.displayName(), status.name(), sorted(assignedRoleCodes));
    }

    public AssignableInternalRoleDto toDto(AssignableInternalRole role) {
        return new AssignableInternalRoleDto(role.code(), role.name());
    }

    private List<String> sorted(Set<String> roleCodes) {
        return roleCodes.stream().sorted().toList();
    }
}
