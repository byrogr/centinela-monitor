# Centinela — Monitoreo de eventos de epilepsia (FASE 1)

Sistema de asistencia y monitoreo en tiempo real que procesa eventos de
OpenSeizureDetector (OSD). Esta Fase 1 incluye el **simulador de eventos** y el
**pipeline completo de backend** (webhook REST → ruteo por severidad → Kafka →
consumidor de alertas), de modo que todo se valida **sin hardware real**.

> Aviso: este software es una capa de alerta **complementaria y redundante**.
> No es un dispositivo médico ni sustituye supervisión clínica.

## Requisitos
- Java 21
- Maven 3.9+
- Docker + Docker Compose

## 1. Levantar Kafka
```bash
docker compose up -d
```
Kafka UI queda en http://localhost:8081

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
  -H "X-OSD-Token: dev-local-secret-change-me" \
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
