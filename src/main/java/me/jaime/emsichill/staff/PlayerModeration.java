package me.jaime.emsichill.staff;

import java.util.ArrayList;
import java.util.List;

/** Estado persistente de moderacion asociado a un nombre conocido. */
record PlayerModeration(String name, MuteRecord mute, List<ModerationEntry> history) {
    PlayerModeration {
        history = new ArrayList<>(history);
    }
}
