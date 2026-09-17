package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.AssignableInternalRoleDto;
import com.meridian.platform.identity.application.dto.ChangeInternalUserRoleRequest;
import com.meridian.platform.identity.application.dto.ChangeInternalUserStatusRequest;
import com.meridian.platform.identity.application.dto.InternalUserDto;
import com.meridian.platform.identity.application.port.in.ManageInternalUserUseCase;
import com.meridian.platform.identity.application.port.in.QueryInternalUsersUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/internal-users")
public class InternalUserAdministrationController {

    private final QueryInternalUsersUseCase query;
    private final ManageInternalUserUseCase commands;

    public InternalUserAdministrationController(
            QueryInternalUsersUseCase query,
            ManageInternalUserUseCase commands
    ) {
        this.query = query;
        this.commands = commands;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity:user:manage')")
    public List<InternalUserDto> getInternalUsers() {
        return query.findAll();
    }

    @GetMapping("/assignable-roles")
    @PreAuthorize("hasAuthority('identity:user:manage')")
    public List<AssignableInternalRoleDto> getAssignableRoles() {
        return query.findAssignableRoles();
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('identity:user:manage')")
    public InternalUserDto changeStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeInternalUserStatusRequest request
    ) {
        return commands.changeStatus(userId, request);
    }

    @PutMapping("/{userId}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('identity:user:manage')")
    public InternalUserDto changeRoleAssignment(
            @PathVariable UUID userId,
            @PathVariable String roleCode,
            @Valid @RequestBody ChangeInternalUserRoleRequest request
    ) {
        return commands.changeRoleAssignment(userId, roleCode, request);
    }
}
