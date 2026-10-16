package org.example.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.example.bootstrap.BootstrapProperties.Admin;
import org.example.bootstrap.BootstrapProperties.Area;
import org.example.entity.HomeAdministrativeAreaEntity;
import org.example.entity.HomeAdministrativeAreaReferenceEntity;
import org.example.entity.Role;
import org.example.entity.UserEntity;
import org.example.entity.UserRoleEntity;
import org.example.repository.HomeAdministrativeAreaReferenceRepository;
import org.example.repository.HomeAdministrativeAreaRepository;
import org.example.repository.UserRepository;
import org.example.repository.UserRoleRepository;
import org.example.service.KeycloakUserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.UUID;

/**
 * Brings an environment to its start state: top-level areas, and per admin a Keycloak account,
 * a register row and the ADMIN role on the area.
 * <p>
 * Idempotent - everything is matched by its natural key (area code, e-mail) and only what is
 * missing is created, so it runs safely on every start. Keycloak owns the user id; the register
 * row takes whatever id Keycloak has, so nothing has to be kept in sync by hand.
 * <p>
 * Runs in one transaction under a Postgres advisory lock, so parallel instances do not race. A
 * Keycloak account created before a rolled-back transaction is found by e-mail on the next run.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BootstrapProperties.class)
public class InitialDataBootstrap implements ApplicationRunner {

    /**
     * Arbitrary, application-wide key of the advisory lock.
     */
    private static final long LOCK_KEY = 4_811_190_281L;

    private static final int TOP_LEVEL = 1;

    private final BootstrapProperties properties;
    private final HomeAdministrativeAreaRepository areaRepository;
    private final HomeAdministrativeAreaReferenceRepository areaReferenceRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final KeycloakUserService keycloakUserService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public InitialDataBootstrap(BootstrapProperties properties,
                                HomeAdministrativeAreaRepository areaRepository,
                                HomeAdministrativeAreaReferenceRepository areaReferenceRepository,
                                UserRepository userRepository,
                                UserRoleRepository userRoleRepository,
                                KeycloakUserService keycloakUserService,
                                JdbcTemplate jdbcTemplate,
                                @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.areaRepository = areaRepository;
        this.areaReferenceRepository = areaReferenceRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.keycloakUserService = keycloakUserService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Bootstrap: {} area(s), {} admin(s)", properties.areas().size(), properties.admins().size());

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForList("SELECT pg_advisory_xact_lock(?)", LOCK_KEY);
            properties.areas().forEach(this::ensureArea);
            properties.admins().forEach(this::ensureAdmin);
        });

        log.info("Bootstrap finished");
    }

    private void ensureArea(Area area) {
        if (areaRepository.findByCode(area.code()).isPresent()) {
            return;
        }

        HomeAdministrativeAreaEntity entity = new HomeAdministrativeAreaEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode(area.code());
        entity.setName(area.name());
        entity.setZone(area.zone());
        entity.setLevelType(TOP_LEVEL);
        areaRepository.saveAndFlush(entity);
        log.info("Bootstrap: created area {}", area.code());
    }

    private void ensureAdmin(Admin admin) {
        String email = admin.email().trim().toLowerCase(Locale.ROOT);

        HomeAdministrativeAreaReferenceEntity area = areaRepository.findByCode(admin.areaCode())
                .map(found -> areaReferenceRepository.getReferenceById(found.getId()))
                .orElseThrow(() -> new IllegalStateException(
                        "app.bootstrap.admins: unknown area code '%s' for %s".formatted(admin.areaCode(), email)));

        UUID id = keycloakUserService.findByEmail(email)
                .map(user -> UUID.fromString(user.getId()))
                .orElseGet(() -> {
                    UUID created = keycloakUserService.create(admin.firstName(), admin.lastName(), email);
                    log.info("Bootstrap: created Keycloak account {} for {}", created, email);
                    return created;
                });

        UserEntity user = userRepository.findById(id).orElseGet(() -> {
            UserEntity entity = new UserEntity(id);
            entity.setHomeAdministrativeArea(area);
            return userRepository.saveAndFlush(entity);
        });

        boolean isAdminOfArea = userRoleRepository.findAllByUserIdAndRole(id, Role.ADMIN).stream()
                .anyMatch(role -> role.getHomeAdministrativeArea() != null
                        && role.getHomeAdministrativeArea().getId().equals(area.getId()));
        if (isAdminOfArea) {
            return;
        }

        UserRoleEntity role = new UserRoleEntity();
        role.setUser(user);
        role.setRole(Role.ADMIN);
        role.setHomeAdministrativeArea(area);
        userRoleRepository.saveAndFlush(role);
        log.info("Bootstrap: granted ADMIN on {} to {}", admin.areaCode(), email);
    }
}
