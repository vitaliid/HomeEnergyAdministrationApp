package org.example.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Start data of an environment: the top-level areas and the first admins, who then create
 * everything else through the API. Differs per environment, so it is configuration (default in
 * {@code bootstrap/initial-data.yml}, overridden per environment) rather than a migration.
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(
        boolean enabled,
        List<Area> areas,
        List<Admin> admins
) {

    public BootstrapProperties {
        areas = areas == null ? List.of() : List.copyOf(areas);
        admins = admins == null ? List.of() : List.copyOf(admins);
    }

    /**
     * A top-level home administrative area, identified by its code.
     *
     * @param zone id of a {@code building_zone} reference row
     */
    public record Area(String code, String name, String zone) {
    }

    /**
     * An admin, identified by e-mail, holding the ADMIN role on the area with the given code.
     */
    public record Admin(String email, String firstName, String lastName, String areaCode) {
    }
}
