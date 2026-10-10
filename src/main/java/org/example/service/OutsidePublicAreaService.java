package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.HomeAdministrativeAreaReferenceEntity;
import org.example.entity.OutsidePublicAreaEntity;
import org.example.exception.BusinessException;
import org.example.exception.ErrorCode;
import org.example.mapper.OutsidePublicAreaMapper;
import org.example.model.OutsidePublicArea;
import org.example.model.OutsidePublicAreaCreate;
import org.example.model.OutsidePublicAreaUpdate;
import org.example.repository.HomeAdministrativeAreaReferenceRepository;
import org.example.repository.OutsidePublicAreaRepository;
import org.example.service.AccessScopeService.CallingAdmin;
import org.example.validation.Din91379TextValidation;
import org.example.validation.Din91379Type;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutsidePublicAreaService {

    private final OutsidePublicAreaRepository repository;
    private final HomeAdministrativeAreaReferenceRepository homeAdministrativeAreaReferenceRepository;
    private final OutsidePublicAreaMapper mapper;
    private final AccessScopeService accessScopeService;
    private final Din91379TextValidation textValidation;

    @Transactional(readOnly = true)
    public Optional<OutsidePublicArea> getById(UUID id) {
        UUID userId = accessScopeService.requireCurrentUserId();
        if (!repository.isVisibleToUser(id, userId)) {
            return Optional.empty();
        }
        return repository.findById(id)
                .map(mapper::toApiModel);
    }

    @Transactional(readOnly = true)
    public List<OutsidePublicArea> listAll() {
        UUID userId = accessScopeService.requireCurrentUserId();
        List<UUID> visibleIds = repository.findAllIdsVisibleToUser(userId);
        if (visibleIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIdInOrderByNameAscHomeAdministrativeAreaIdAsc(visibleIds).stream()
                .map(mapper::toApiModel)
                .toList();
    }

    @Transactional
    public OutsidePublicArea create(OutsidePublicAreaCreate dto) {
        CallingAdmin admin = accessScopeService.requireCallingAdmin();

        HomeAdministrativeAreaReferenceEntity parent = homeAdministrativeAreaReferenceRepository
                .findById(dto.getHomeAdministrativeAreaId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARENT));
        accessScopeService.requireHomeAdministrativeAreaInScope(admin, parent.getId());

        OutsidePublicAreaEntity entity = new OutsidePublicAreaEntity();
        entity.setName(textValidation.normalizeAndValidate(requireName(dto.getName()), Din91379Type.DATATYPE_C));
        entity.setHomeAdministrativeArea(parent);
        entity.setZone(parent.getZone());

        try {
            OutsidePublicAreaEntity saved = repository.saveAndFlush(entity);
            return mapper.toApiModel(saved);
        } catch (DataIntegrityViolationException ex) {
            handleDataIntegrityViolation(ex);
            throw ex;
        }
    }

    @Transactional
    public OutsidePublicArea patch(UUID id, OutsidePublicAreaUpdate dto) {
        CallingAdmin admin = accessScopeService.requireCallingAdmin();

        OutsidePublicAreaEntity entity = repository.findById(id)
                .filter(outsidePublicArea -> admin.coversOutsidePublicArea(outsidePublicArea.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        if (dto.getName() != null) {
            entity.setName(textValidation.normalizeAndValidate(requireName(dto.getName()), Din91379Type.DATATYPE_C));
        }

        if (dto.getHomeAdministrativeAreaId() != null) {
            HomeAdministrativeAreaReferenceEntity homeAdministrativeArea = homeAdministrativeAreaReferenceRepository
                    .findById(dto.getHomeAdministrativeAreaId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARENT));
            accessScopeService.requireHomeAdministrativeAreaInScope(admin, homeAdministrativeArea.getId());
            entity.setHomeAdministrativeArea(homeAdministrativeArea);
            entity.setZone(homeAdministrativeArea.getZone());
        }

        try {
            OutsidePublicAreaEntity saved = repository.saveAndFlush(entity);
            return mapper.toApiModel(saved);
        } catch (DataIntegrityViolationException ex) {
            handleDataIntegrityViolation(ex);
            throw ex;
        }
    }

    @Transactional
    public boolean delete(UUID id) {
        CallingAdmin admin = accessScopeService.requireCallingAdmin();

        if (!admin.coversOutsidePublicArea(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_FORMAT);
        }
        return trimmed;
    }

    private void handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Throwable rootCause = ex.getRootCause();
        String message = rootCause != null && rootCause.getMessage() != null
                ? rootCause.getMessage().toLowerCase(Locale.ROOT)
                : "";

        if (message.contains("uq_outside_public_area_home_administrative_area_name")) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME_IN_PARENT);
        }

        if (message.contains("fk_outside_public_area_home_administrative_area")) {
            throw new BusinessException(ErrorCode.INVALID_PARENT);
        }
    }
}
