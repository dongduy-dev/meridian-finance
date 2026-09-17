package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.ChangeInternalUserRoleRequest;
import com.meridian.platform.identity.application.dto.ChangeInternalUserStatusRequest;
import com.meridian.platform.identity.application.dto.InternalUserDto;

import java.util.UUID;

public interface ManageInternalUserUseCase {

    InternalUserDto changeStatus(UUID userId, ChangeInternalUserStatusRequest request);

    InternalUserDto changeRoleAssignment(UUID userId, String roleCode, ChangeInternalUserRoleRequest request);
}
