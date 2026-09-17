package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.AssignableInternalRoleDto;
import com.meridian.platform.identity.application.dto.InternalUserDto;

import java.util.List;

public interface QueryInternalUsersUseCase {

    List<InternalUserDto> findAll();

    List<AssignableInternalRoleDto> findAssignableRoles();
}
