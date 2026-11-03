package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.BuildingConsumptionEntity;
import org.example.entity.EsrConsumptionEntity;
import org.example.entity.HeatBuildingConsumptionEntity;
import org.example.entity.YardPublicAreaEntity;
import org.example.entity.Role;
import org.example.exception.BusinessException;
import org.example.exception.ErrorCode;
import org.example.mapper.EsrConsumptionMapper;
import org.example.model.AbstractBuildingConsumption;
import org.example.model.AbstractBuildingConsumptionInput;
import org.example.model.BuildingConsumptionInput;
import org.example.model.HeatBuildingConsumptionInput;
import org.example.repository.EsrConsumptionRepository;
import org.example.repository.YardPublicAreaRepository;
import org.example.validation.EsrConsumptionValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Building consumption entries of a yard public area in the Energy Supply Register.
 */
@Service
@RequiredArgsConstructor
public class EsrConsumptionService {

    private final EsrConsumptionRepository repository;
    private final YardPublicAreaRepository yardPublicAreaRepository;
    private final EsrConsumptionMapper mapper;
    private final EsrConsumptionValidator validator;
    private final AccessScopeService accessScopeService;

    @Transactional(readOnly = true)
    public List<AbstractBuildingConsumption> getAll(UUID yardPublicAreaId) {
        YardPublicAreaEntity yardPublicArea = requireYardPublicArea(yardPublicAreaId);
        accessScopeService.requireAnyRoleOnYardPublicArea(Role.ESR_ROLES, yardPublicArea.getId());

        return repository.findAllByYardPublicAreaIdOrderByStartAscCreatedAtAsc(yardPublicArea.getId()).stream()
                .map(mapper::toApiModel)
                .toList();
    }

    @Transactional
    public AbstractBuildingConsumption create(UUID yardPublicAreaId, AbstractBuildingConsumptionInput input) {
        YardPublicAreaEntity yardPublicArea = requireYardPublicArea(yardPublicAreaId);
        accessScopeService.requireRoleOnYardPublicArea(Role.ESR_EDITOR, yardPublicArea.getId());

        validator.validate(input);

        EsrConsumptionEntity entity = switch (input) {
            case HeatBuildingConsumptionInput heat -> toEntity(heat);
            case BuildingConsumptionInput building -> toEntity(building);
            default -> throw new IllegalStateException("Unknown input type: " + input.getClass());
        };
        entity.setYardPublicArea(yardPublicArea);

        EsrConsumptionEntity saved = repository.saveAndFlush(entity);
        return mapper.toApiModel(saved);
    }

    @Transactional
    public AbstractBuildingConsumption update(UUID yardPublicAreaId, UUID id, AbstractBuildingConsumptionInput input) {
        YardPublicAreaEntity yardPublicArea = requireYardPublicArea(yardPublicAreaId);
        accessScopeService.requireRoleOnYardPublicArea(Role.ESR_EDITOR, yardPublicArea.getId());

        // id and yard public area id are checked together: an entry of another entity is "not found"
        EsrConsumptionEntity entity = repository.findByIdAndYardPublicAreaId(id, yardPublicArea.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        validator.validate(input);

        // the type cannot be changed by update
        switch (input) {
            case HeatBuildingConsumptionInput heat when entity instanceof HeatBuildingConsumptionEntity heatEntity ->
                    fill(heatEntity, heat);
            case BuildingConsumptionInput building when entity instanceof BuildingConsumptionEntity buildingEntity ->
                    fill(buildingEntity, building);
            default -> throw new BusinessException(ErrorCode.CONSUMPTION_TYPE_CHANGE_NOT_ALLOWED);
        }

        EsrConsumptionEntity saved = repository.saveAndFlush(entity);
        return mapper.toApiModel(saved);
    }

    @Transactional
    public void delete(UUID yardPublicAreaId, UUID id) {
        YardPublicAreaEntity yardPublicArea = requireYardPublicArea(yardPublicAreaId);
        accessScopeService.requireRoleOnYardPublicArea(Role.ESR_EDITOR, yardPublicArea.getId());

        EsrConsumptionEntity entity = repository.findByIdAndYardPublicAreaId(id, yardPublicArea.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        repository.delete(entity);
    }

    private YardPublicAreaEntity requireYardPublicArea(UUID yardPublicAreaId) {
        return yardPublicAreaRepository.findById(yardPublicAreaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private BuildingConsumptionEntity toEntity(BuildingConsumptionInput input) {
        BuildingConsumptionEntity entity = new BuildingConsumptionEntity();
        fill(entity, input);
        return entity;
    }

    private HeatBuildingConsumptionEntity toEntity(HeatBuildingConsumptionInput input) {
        HeatBuildingConsumptionEntity entity = new HeatBuildingConsumptionEntity();
        fill(entity, input);
        return entity;
    }

    private void fill(BuildingConsumptionEntity entity, BuildingConsumptionInput input) {
        fillBase(entity, input.getQuantity(), input.getStart(), input.getEnd());
    }

    private void fill(HeatBuildingConsumptionEntity entity, HeatBuildingConsumptionInput input) {
        fillBase(entity, input.getQuantity(), input.getStart(), input.getEnd());
        entity.setIsWeatherAdjusted(input.getIsWeatherAdjusted());
    }

    // TODO master data: take category and carrier from the input once the tables exist
    private void fillBase(EsrConsumptionEntity entity, String quantity, LocalDate start, LocalDate end) {
        entity.setQuantity(new BigDecimal(quantity.trim()));
        entity.setStart(start);
        entity.setEnd(end);
    }
}
