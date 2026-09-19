package com.rmsolutions.centinela.watchdog.schedule;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas. Fuera del perfil simulador, que no vigila nada.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Configuration
@EnableScheduling
@Profile("!simulator")
public class SchedulingConfig {
}
