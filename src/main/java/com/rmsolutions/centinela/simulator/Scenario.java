package com.rmsolutions.centinela.simulator;

/**
 * Escenarios que el simulador puede reproducir.
 */
public enum Scenario {
    NORMAL,       // Actividad basal saludable.
    SEIZURE,      // Linea de tiempo realista: normal -> pre-ictal -> convulsion -> recuperacion.
    FALL,         // Deteccion de caida.
    LOW_BATTERY,  // Reloj con bateria critica.
    DISCONNECT,   // Perdida de conexion con el reloj.
    RANDOM        // Mezcla aleatoria para pruebas de estres.
}
