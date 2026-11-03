package org.example.mapper;

import org.example.entity.BuildingConsumptionEntity;
import org.example.entity.EsrConsumptionEntity;
import org.example.entity.HeatBuildingConsumptionEntity;
import org.example.model.AbstractBuildingConsumption;
import org.example.model.BuildingConsumption;
import org.example.model.EnergyCarrierConsumption;
import org.example.model.HeatBuildingConsumption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = YardPublicAreaMapper.class)
public interface EsrConsumptionMapper {

    default AbstractBuildingConsumption toApiModel(EsrConsumptionEntity entity) {
        return switch (entity) {
            case HeatBuildingConsumptionEntity heat -> toApiModel(heat);
            case BuildingConsumptionEntity building -> toApiModel(building);
            default -> throw new IllegalStateException("Unknown ESR consumption type: " + entity.getClass());
        };
    }

    // TODO master data: map category once the BWZK category table exists
    @Mapping(target = "objectType", constant = BuildingConsumptionEntity.DISCRIMINATOR)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "consumption", source = "entity")
    BuildingConsumption toApiModel(BuildingConsumptionEntity entity);

    // TODO master data: map category once the BWZK category table exists
    @Mapping(target = "objectType", constant = HeatBuildingConsumptionEntity.DISCRIMINATOR)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "consumption", source = "entity")
    HeatBuildingConsumption toApiModel(HeatBuildingConsumptionEntity entity);

    // TODO master data: map unit once the energy carrier unit table exists
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "unit", ignore = true)
    @Mapping(target = "quantity", expression = "java(entity.getQuantity().stripTrailingZeros().toPlainString())")
    EnergyCarrierConsumption toEnergyCarrierConsumption(EsrConsumptionEntity entity);
}
