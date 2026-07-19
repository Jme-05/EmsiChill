package me.jaime.emsichill.staff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.util.CommandSuggestions;

/** Valida sintaxis y permisos de moderación; las reglas viven en servicios dedicados. */
public final class StaffCommand implements CommandExecutor, TabCompleter {
    private static final int MAX_FREEZE_SECONDS = 86_400;
    private static final List<String> FREEZE_DURATIONS = List.of("10", "30", "60", "300");

    private final Main plugin;
    private final StaffService staff;
    private final InspectionService inspections;
    private final FreezeService freezes;

    public StaffCommand(
        final Main plugin,
        final StaffService staff,
        final InspectionService inspections,
        final FreezeService freezes
    ) {
        this.plugin = plugin;
        this.staff = staff;
        this.inspections = inspections;
        this.freezes = freezes;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.plugin.moduleEnabled("staff")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "staffchat" -> this.staffChat(sender, args);
            case "vanish" -> this.vanish(sender, args);
            case "vanishlist" -> this.vanishList(sender);
            case "staffmode" -> this.staffMode(sender, args);
            case "invsee" -> this.inspect(sender, args, InspectionService.Type.INVENTORY);
            case "enderchestsee" -> this.inspect(sender, args, InspectionService.Type.ENDER_CHEST);
            case "freeze" -> this.freeze(sender, args);
            case "slay" -> this.slay(sender, args);
            default -> true;
        };
    }

    private boolean staffChat(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.staffchat")) return this.noPermission(sender);
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("toggle"))) {
            if (!(sender instanceof Player player)) {
                this.plugin.messages().send(sender, "staff.staffchat-usage");
                return true;
            }
            boolean enabled = this.staff.toggleStaffChat(player);
            this.plugin.messages().send(player, enabled ? "staff.staffchat-on" : "staff.staffchat-off");
            return true;
        }
        this.staff.sendStaffMessage(sender, String.join(" ", args));
        return true;
    }

    private boolean vanish(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.vanish")) return this.noPermission(sender);
        Player target = this.resolveTarget(sender, args, "emsichill.vanish.others", "staff.vanish-usage");
        if (target == null) return true;
        boolean enabled = this.staff.toggleVanish(target);
        this.plugin.messages().send(target, enabled ? "staff.vanish-on" : "staff.vanish-off");
        if (!sender.equals(target)) {
            this.plugin.messages().send(sender, "staff.vanish-other", "{player}", target.getName());
        }
        return true;
    }

    private boolean vanishList(final CommandSender sender) {
        if (!sender.hasPermission("emsichill.vanish.see")) return this.noPermission(sender);
        List<String> names = this.staff.vanishedNames();
        this.plugin.messages().send(sender, "staff.vanish-list", "{players}",
            names.isEmpty() ? "-" : String.join(", ", names));
        return true;
    }

    private boolean staffMode(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.staffmode")) return this.noPermission(sender);
        Player target = this.resolveTarget(sender, args, "emsichill.staffmode.others", "staff.staffmode-usage");
        if (target == null) return true;
        boolean enabled = this.staff.toggleStaffMode(target);
        this.plugin.messages().send(target, enabled ? "staff.staffmode-on" : "staff.staffmode-off");
        if (!sender.equals(target)) {
            this.plugin.messages().send(sender, "staff.staffmode-other", "{player}", target.getName());
        }
        return true;
    }

    private boolean inspect(final CommandSender sender, final String[] args, final InspectionService.Type type) {
        if (!(sender instanceof Player viewer)) {
            this.plugin.messages().send(sender, "general.only-players");
            return true;
        }
        if (args.length != 1) {
            this.plugin.messages().send(sender,
                type == InspectionService.Type.INVENTORY ? "staff.invsee-usage" : "staff.enderchestsee-usage");
            return true;
        }
        Player target = this.findOnline(args[0]);
        if (target == null) {
            this.plugin.messages().send(sender, "staff.player-not-found");
            return true;
        }
        InspectionService.OpenResult result = this.inspections.open(viewer, target, type);
        if (result == InspectionService.OpenResult.NO_PERMISSION) return this.noPermission(sender);
        String mode = result == InspectionService.OpenResult.OPENED_EDITABLE ? "editable" : "read-only";
        this.plugin.messages().send(sender, "staff.inspection-opened-" + mode,
            "{player}", target.getName());
        this.plugin.audit().log("STAFF_INSPECTION", "actor=" + sender.getName() + " target=" + target.getName()
            + " type=" + type.name() + " mode=" + mode);
        return true;
    }

    private boolean freeze(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.freeze")) return this.noPermission(sender);
        if (args.length < 1 || args.length > 2) {
            this.plugin.messages().send(sender, "staff.freeze-usage");
            return true;
        }
        Player target = this.findOnline(args[0]);
        if (target == null) {
            this.plugin.messages().send(sender, "staff.player-not-found");
            return true;
        }
        if (args.length == 2) return this.timedFreeze(sender, target, args[1]);

        boolean enabled = this.freezes.toggle(target.getUniqueId());
        this.plugin.messages().send(target, enabled ? "staff.frozen" : "staff.unfrozen");
        if (!sender.equals(target)) {
            this.plugin.messages().send(sender, enabled ? "staff.freeze-enabled" : "staff.freeze-disabled",
                "{player}", target.getName());
        }
        this.plugin.audit().log(enabled ? "PLAYER_FREEZE" : "PLAYER_UNFREEZE",
            "actor=" + sender.getName() + " target=" + target.getName());
        return true;
    }

    private boolean timedFreeze(final CommandSender sender, final Player target, final String value) {
        int seconds;
        try {
            seconds = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            this.plugin.messages().send(sender, "staff.freeze-seconds-invalid");
            return true;
        }
        if (seconds < 1 || seconds > MAX_FREEZE_SECONDS) {
            this.plugin.messages().send(sender, "staff.freeze-seconds-invalid");
            return true;
        }

        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        this.freezes.freezeFor(targetId, seconds, () -> {
            Player onlineTarget = Bukkit.getPlayer(targetId);
            if (onlineTarget != null) this.plugin.messages().send(onlineTarget, "staff.freeze-expired");
            this.plugin.audit().log("PLAYER_FREEZE_EXPIRED", "target=" + targetName);
        });
        this.plugin.messages().send(target, "staff.frozen-timed", "{seconds}", Integer.toString(seconds));
        if (!sender.equals(target)) {
            this.plugin.messages().send(sender, "staff.freeze-timed-enabled",
                "{player}", targetName, "{seconds}", Integer.toString(seconds));
        }
        this.plugin.audit().log("PLAYER_TIMED_FREEZE", "actor=" + sender.getName()
            + " target=" + targetName + " seconds=" + seconds);
        return true;
    }

    private boolean slay(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.slay")) return this.noPermission(sender);
        if (args.length != 1) {
            this.plugin.messages().send(sender, "staff.slay-usage");
            return true;
        }
        Player target = this.findOnline(args[0]);
        if (target == null) {
            this.plugin.messages().send(sender, "staff.player-not-found");
            return true;
        }
        if (target.isDead() || target.getHealth() <= 0.0D) {
            this.plugin.messages().send(sender, "staff.slay-already-dead", "{player}", target.getName());
            return true;
        }

        this.plugin.messages().send(target, "staff.slain");
        target.setHealth(0.0D);
        if (!sender.equals(target)) {
            this.plugin.messages().send(sender, "staff.slay-success", "{player}", target.getName());
        }
        this.plugin.audit().log("PLAYER_SLAY", "actor=" + sender.getName() + " target=" + target.getName());
        return true;
    }

    private Player resolveTarget(
        final CommandSender sender,
        final String[] args,
        final String othersPermission,
        final String usageKey
    ) {
        if (args.length == 0 && sender instanceof Player player) return player;
        if (args.length == 1 && sender.hasPermission(othersPermission)) {
            Player target = this.findOnline(args[0]);
            if (target == null) this.plugin.messages().send(sender, "staff.player-not-found");
            return target;
        }
        this.plugin.messages().send(sender, usageKey);
        return null;
    }

    private Player findOnline(final String name) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) return player;
        }
        return null;
    }

    private boolean noPermission(final CommandSender sender) {
        this.plugin.messages().send(sender, "general.no-permission");
        return true;
    }

    @Override
    public List<String> onTabComplete(
        final CommandSender sender,
        final Command command,
        final String alias,
        final String[] args
    ) {
        if (command.getName().equalsIgnoreCase("staffchat") && args.length == 1) {
            return CommandSuggestions.filter(List.of("toggle"), args[0]);
        }
        if (command.getName().equalsIgnoreCase("freeze") && args.length == 2) {
            return CommandSuggestions.filter(FREEZE_DURATIONS, args[1]);
        }
        if (args.length != 1 || !List.of("vanish", "staffmode", "invsee", "enderchestsee", "freeze", "slay")
            .contains(command.getName().toLowerCase(Locale.ROOT))) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
        return CommandSuggestions.filter(names, args[0]);
    }
}
