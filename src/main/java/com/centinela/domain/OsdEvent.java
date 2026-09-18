package com.centinela.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa un evento crudo emitido por OpenSeizureDetector (OSD).
 *
 * Se usa un 'record' inmutable: una vez recibido, el evento no debe mutar
 * mientras viaja por el pipeline (auditabilidad de un sistema critico).
 *
 * Nota sobre 'Time': OSD lo envia con mayuscula inicial, por eso el @JsonProperty.
 * Se ignoran campos desconocidos para tolerar cambios de version de OSD sin romper.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsdEvent(
        @JsonProperty("Time") String time,
        @JsonProperty("alarmState") int alarmState,
        @JsonProperty("alarmPhrase") String alarmPhrase,
        @JsonProperty("maxFreq") double maxFreq,
        @JsonProperty("maxVal") double maxVal,
        @JsonProperty("specPower") double specPower,
        @JsonProperty("roiPower") double roiPower,
        @JsonProperty("heartRate") Integer heartRate,
        @JsonProperty("batteryLevel") Integer batteryLevel,
        @JsonProperty("watchConnected") Boolean watchConnected
) {
}
