package com.centinela.domain;

/**
 * Traduce el 'alarmState' numerico de OSD a un valor semantico.
 *
 * IMPORTANTE: los codigos 0=OK, 1=WARNING, 2=ALARM, 3=FALL son los estandar de OSD.
 * Los codigos 5 (MANUAL) y 6 (MUTE) deben verificarse contra la version de OSD
 * que uses antes de conectar hardware real. Cualquier codigo no mapeado cae en
 * UNKNOWN y, por diseno fail-safe, se trata como algo que requiere atencion.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public enum AlarmState {
    OK(0),
    WARNING(1),
    ALARM(2),
    FALL(3),
    MANUAL_ALARM(5),
    MUTE(6),
    UNKNOWN(-1);

    private final int code;

    AlarmState(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static AlarmState fromCode(int code) {
        for (AlarmState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
