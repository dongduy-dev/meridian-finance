package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.AssignableInternalRoleDto;
import com.meridian.platform.identity.application.dto.InternalUserDto;
import com.meridian.platform.identity.application.mapper.InternalUserMapper;
import com.meridian.platform.identity.application.port.in.QueryInternalUsersUseCase;
import com.meridian.platform.identity.application.port.out.InternalUserAdministrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryInternalUsersService implements QueryInternalUsersUseCase {

    private final InternalUserAdministrationRepository repository;
    private final InternalUserMapper mapper;

    public QueryInternalUsersService(InternalUserAdministrationRepository repository, InternalUserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalUserDto> findAll() {
        return repository.findAllInternalUsers().stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignableInternalRoleDto> findAssignableRoles() {
        return repository.findAssignableRoles().stream().map(mapper::toDto).toList();
    }
}
