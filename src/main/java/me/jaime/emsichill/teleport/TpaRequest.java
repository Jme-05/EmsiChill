package me.jaime.emsichill.teleport;

import java.util.UUID;

/** Solicitud TPA pendiente, su dirección y el instante en que deja de ser válida. */
public record TpaRequest(UUID requester, UUID target, boolean here, long expiresAt) {
}