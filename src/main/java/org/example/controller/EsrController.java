package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.api.EsrApi;
import org.example.model.AbstractBuildingConsumption;
import org.example.model.AbstractBuildingConsumptionInput;
import org.example.model.BuildingConsumptionCategory;
import org.example.model.EnergyCarrierUnit;
import org.example.service.EsrConsumptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Energy Supply Register (ESR): building consumption entries of a yard public area and the ESR master data.
 */
@RestController
@RequiredArgsConstructor
public class EsrController implements EsrApi {

    private final EsrConsumptionService esrConsumptionService;

    // --- building consumption entries of a yard public area ---

    @Override
    public ResponseEntity<List<AbstractBuildingConsumption>> getBuildingEsrEntries(UUID id) {
        return ResponseEntity.ok(esrConsumptionService.getAll(id));
    }

    @Override
    public ResponseEntity<AbstractBuildingConsumption> createBuildingEsrEntry(
            UUID id,
            AbstractBuildingConsumptionInput abstractBuildingConsumptionInput) {
        return ResponseEntity.ok(esrConsumptionService.create(id, abstractBuildingConsumptionInput));
    }

    @Override
    public ResponseEntity<AbstractBuildingConsumption> replaceBuildingEsrEntry(
            UUID yardPublicAreaId,
            UUID id,
            AbstractBuildingConsumptionInput abstractBuildingConsumptionInput) {
        return ResponseEntity.ok(esrConsumptionService.update(yardPublicAreaId, id, abstractBuildingConsumptionInput));
    }

    @Override
    public ResponseEntity<Void> deleteBuildingEsrEntry(UUID yardPublicAreaId, UUID id) {
        esrConsumptionService.delete(yardPublicAreaId, id);
        return ResponseEntity.ok().build();
    }

    // --- master data ---

    @Override
    public ResponseEntity<List<BuildingConsumptionCategory>> listBuildingConsumptionCategories() {
        // TODO master data: return the BWZK categories once the table exists
        return ResponseEntity.ok(List.of());
    }

    @Override
    public ResponseEntity<List<EnergyCarrierUnit>> listEnergyCarrierUnits() {
        // TODO master data: return the energy carrier units once the table exists
        return ResponseEntity.ok(List.of());
    }
}
