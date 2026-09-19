# Lenzo — Acompañamiento y monitoreo de eventos de epilepsia

> **Lenzo** es el nombre comercial. `centinela` es el nombre clave del proyecto y
> es lo que verás en el código: artefacto `centinela-monitor`, paquete
> `com.rmsolutions.centinela`, contenedores y base de datos `centinela-*`.

Sistema de asistencia y monitoreo en tiempo real que procesa eventos de
OpenSeizureDetector (OSD): webhook REST → ruteo por severidad → Kafka →
persistencia y consumidores. El **simulador** permite validarlo todo **sin
hardware real**.

Estado: **Fase 2** (persistencia, historial y vigilante de silencio).

> Aviso: este software es una capa de alerta **complementaria y redundante**.
> No es un dispositivo médico ni sustituye supervisión clínica.

## Requisitos
- Java 21
- Maven 3.9+
- Docker + Docker Compose

## 1. Levantar la infraestructura local
```bash
docker compose up -d
```
Levanta Kafka, PostgreSQL y Redis:

| Servicio   | Puerto | Notas                                            |
|------------|--------|--------------------------------------------------|
| Kafka      | 9092   | UI en http://localhost:8081                      |
| PostgreSQL | 5432   | base `centinela`, usuario `centinela` (dev)      |
| Redis      | 6379   | AOF activado                                     |

Postgres y Redis tienen `healthcheck`: espera a que esten `healthy` antes de arrancar el backend.

## 1.1 Base de datos

Flyway aplica las migraciones al arrancar el backend. Hay dos ubicaciones:

- `db/migration` — esquema (V1 relacional, V2 eventos particionados, V3 incidencias de silencio).
- `db/seed` — datos de desarrollo (V4). **En produccion:** `FLYWAY_LOCATIONS=classpath:db/migration`
  para que el seed nunca se aplique.

API key del dispositivo de desarrollo (solo local): `lenzo-dev-child-001-0123456789abcdef`.
La base guarda unicamente su SHA-256. El webhook la exige en la cabecera `X-Device-Key`;
el secreto compartido `X-OSD-Token` de la Fase 1 ya no existe.

La tabla `event` es append-only: un trigger rechaza `UPDATE` y `DELETE`. Para limpiar datos de
prueba en local usa `TRUNCATE event`.

## 1.2 Vigilante de silencio

Avisa cuando un reloj deja de emitir. Corre como tarea programada:

| Propiedad | Default | Qué es |
|---|---|---|
| `app.watchdog.check-interval-ms` | 30000 | cada cuánto revisa |
| `app.watchdog.initial-delay-ms` | 20000 | margen al arrancar |
| `device.silence_threshold_seconds` | 240 | por dispositivo, en la BD |

No notifica por su cuenta: inyecta un evento sintético (`alarmPhrase = SILENCE`) en el mismo
pipeline que los eventos reales, así que lo clasifica el mismo `SeverityRouter` y queda en el
historial. Para probarlo sin esperar, baja el umbral:

```sql
UPDATE device SET silence_threshold_seconds = 60;
```

## 2. Compilar
```bash
mvn clean package
```

## 3. Arrancar el BACKEND (recibe, rutea, consume)
```bash
java -jar target/centinela-monitor-0.1.0-SNAPSHOT.jar
```
Escucha el webhook en `POST http://localhost:8080/api/v1/osd/events`.

## 4. Arrancar el SIMULADOR (en otra terminal)
```bash
java -jar target/centinela-monitor-0.1.0-SNAPSHOT.jar --spring.profiles.active=simulator
```
Verás en los logs del backend cómo cada evento se clasifica y rutea, y cómo el
consumidor detona (simuladamente) la notificación de emergencia cuando llega una
convulsión.

### Cambiar el escenario del simulador
Por línea de comandos:
```bash
java -jar target/centinela-monitor-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=simulator \
  --app.simulator.scenario=FALL \
  --app.simulator.event-count=10 \
  --app.simulator.interval-ms=1000
```
Escenarios: `NORMAL | SEIZURE | FALL | LOW_BATTERY | DISCONNECT | RANDOM`.

## 5. Probar el webhook manualmente (sin simulador)
```bash
curl -X POST http://localhost:8080/api/v1/osd/events \
  -H "Content-Type: application/json" \
  -H "X-Device-Key: lenzo-dev-child-001-0123456789abcdef" \
  -d '{"Time":"2026-09-18 00:05:32","alarmState":2,"alarmPhrase":"ALARM","maxFreq":5.4,"maxVal":1250.3,"specPower":4500.1,"roiPower":3800.8,"heartRate":134,"batteryLevel":88,"watchConnected":true}'
```

## Estrategia de tópicos
| Tópico                | Contenido                                            |
|-----------------------|------------------------------------------------------|
| `osd.events.raw`      | TODOS los eventos (auditoría / replay / analítica)   |
| `osd.events.telemetry`| Eventos normales (latido / salud OK)                 |
| `osd.alerts.warning`  | Pre-ictal, batería baja, reloj desconectado          |
| `osd.alerts.critical` | Convulsión / caída → notificación de emergencia      |

## Tests
```bash
mvn test
```

## Estructura
```
ingest/     -> webhook REST (recibe el JSON de OSD)
routing/    -> SeverityRouter: decide severidad y tópicos (fail-safe)
producer/   -> publica en Kafka con clave = childId (orden por-persona)
consumer/   -> escucha alertas y llama a NotificationService
notification-> stub de emergencia (Fase 3: voz/SMS/push reales)
simulator/  -> genera eventos OSD realistas (reemplaza al hardware)
```
