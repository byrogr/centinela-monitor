package com.rmsolutions.centinela.registry.application;

import com.rmsolutions.centinela.registry.domain.ApiKeys;
import com.rmsolutions.centinela.registry.domain.Device;
import com.rmsolutions.centinela.registry.domain.Patient;
import com.rmsolutions.centinela.registry.persistence.DeviceRepository;
import com.rmsolutions.centinela.registry.persistence.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del alta, verificacion y revocacion de credenciales de dispositivo,
 * contra un PostgreSQL real.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration,classpath:db/seed"
})
@Testcontainers
class DeviceRegistryServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired
    private DeviceRepository devices;
    @Autowired
    private PatientRepository patients;

    private DeviceRegistryService service;
    private Patient patient;

    @BeforeEach
    void setUp() {
        service = new DeviceRegistryService(devices, patients);
        patient = patients.findByCode("child-001").orElseThrow();
    }

    @Test
    void theKeySeededByTheMigrationVerifiesWithTheJavaHasher() {
        // Esta es la prueba que cierra el circulo de la tarea 3: la migracion V4
        // calculo el hash con encode(sha256(...),'hex') de PostgreSQL. Si el
        // esquema de Java no fuera identico, el dispositivo de desarrollo no
        // podria autenticarse y nadie se enteraria hasta probarlo a mano.
        assertThat(service.verify("lenzo-dev-child-001-0123456789abcdef"))
                .as("el hash de Java debe coincidir con el que calculo el SQL")
                .isPresent();
    }

    @Test
    void registeringReturnsTheKeyOnceAndTheDatabaseStoresOnlyItsHash() {
        RegisteredDevice registered = service.register(patient.getId(), "Celular de prueba", 240);

        assertThat(registered.plainApiKey()).startsWith(ApiKeys.PREFIX);

        Device stored = devices.findById(registered.deviceId()).orElseThrow();
        assertThat(stored.getApiKeyHash())
                .as("la key en claro no puede estar en la base")
                .isNotEqualTo(registered.plainApiKey())
                .isEqualTo(ApiKeys.hash(registered.plainApiKey()));
        assertThat(stored.getApiKeyPrefix()).isEqualTo(ApiKeys.prefix(registered.plainApiKey()));
        assertThat(stored.isActive()).isTrue();
    }

    @Test
    void aFreshlyIssuedKeyVerifiesAndResolvesToTheRightDevice() {
        RegisteredDevice registered = service.register(patient.getId(), "Celular nuevo", 300);

        assertThat(service.verify(registered.plainApiKey()))
                .isPresent()
                .get()
                .satisfies(d -> {
                    assertThat(d.deviceId()).isEqualTo(registered.deviceId());
                    assertThat(d.patientCode())
                            .as("la ingesta lo usa como clave de particion en Kafka")
                            .isEqualTo("child-001");
                    assertThat(d.patientId()).isEqualTo(patient.getId());
                    assertThat(d.silenceThresholdSeconds())
                            .as("el umbral del vigilante viaja con la identidad (tarea 9)")
                            .isEqualTo(300);
                });
    }

    @Test
    void anUnknownKeyDoesNotVerify() {
        assertThat(service.verify(ApiKeys.generate()))
                .as("se falla CERRADO: sin credencial valida no se ingiere nada")
                .isEmpty();
        assertThat(service.verify("cualquier cosa")).isEmpty();
        assertThat(service.verify("")).isEmpty();
        assertThat(service.verify(null)).isEmpty();
    }

    @Test
    void aRevokedDeviceStopsVerifyingButKeepsItsHistory() {
        RegisteredDevice registered = service.register(patient.getId(), "Celular perdido", 240);
        assertThat(service.verify(registered.plainApiKey())).isPresent();

        service.revoke(registered.deviceId());

        assertThat(service.verify(registered.plainApiKey()))
                .as("revocar debe cortar la ingesta de inmediato")
                .isEmpty();
        assertThat(devices.findById(registered.deviceId()))
                .as("la fila no se borra: los eventos ya stored apuntan a ella")
                .isPresent()
                .get()
                .satisfies(d -> {
                    assertThat(d.isActive()).isFalse();
                    assertThat(d.getRevokedAt()).isNotNull();
                });
    }

    @Test
    void twoDevicesOfTheSamePatientGetDifferentKeys() {
        RegisteredDevice first = service.register(patient.getId(), "Celular A", 240);
        RegisteredDevice second = service.register(patient.getId(), "Celular B", 240);

        assertThat(first.plainApiKey()).isNotEqualTo(second.plainApiKey());
        assertThat(service.verify(first.plainApiKey()).orElseThrow().deviceId())
                .isNotEqualTo(service.verify(second.plainApiKey()).orElseThrow().deviceId());
    }

    @Test
    void theResultDoesNotLeakTheKeyWhenPrinted() {
        RegisteredDevice registered = service.register(patient.getId(), "Celular", 240);

        assertThat(registered.toString())
                .as("si esto acaba en un log, la credencial no puede ir dentro")
                .doesNotContain(registered.plainApiKey())
                .contains("***");
    }

    @Test
    void aDeviceCannotBeRegisteredToAMissingPatient() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.register(missing, "Celular fantasma", 240))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missing.toString());
    }
}
