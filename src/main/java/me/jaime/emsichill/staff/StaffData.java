package me.jaime.emsichill.staff;

import java.util.Map;
import java.util.Set;

/** Estado persistente del módulo, separado de sus reglas y comandos. */
record StaffData(Set<String> vanished, Map<String, StaffSnapshot> snapshots) {
}
