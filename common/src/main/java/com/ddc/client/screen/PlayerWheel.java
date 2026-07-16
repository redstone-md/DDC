package com.ddc.client.screen;

import com.ddc.character.CharacterSheet;
import com.ddc.client.ClientRules;
import com.ddc.network.ClassSummary;
import com.ddc.network.RulesPayload;
import com.ddc.rules.ClassFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * What goes on a player's wheel.
 *
 * <p>Two wheels, really. A character who has not been made yet gets the one that makes them: pick a
 * class, pick a race, and nothing else, because nothing else works yet. A character who exists gets
 * the one that plays them.
 *
 * <p>Everything a player can do is here, so nothing has to be typed. The lists come from the server's
 * own data packs, which is why an addon's class appears on the wheel with no client update.
 *
 * <p>Spells are aimed at whatever the player is looking at, and its UUID goes in the command, because
 * that is what the entity selector takes: the wheel sends a command a player could have typed.
 */
@Environment(EnvType.CLIENT)
public final class PlayerWheel {

    private PlayerWheel() {
    }

    /** The wheel a player sees when they press the key. */
    public static WheelScreen forPlayer(CharacterSheet sheet, Optional<ClassSummary> summary) {
        if (sheet == null || !sheet.hasClass() || summary.isEmpty()) {
            return new WheelScreen(Component.translatable("ddc.wheel.create"), creation());
        }
        return new WheelScreen(Component.literal(summary.get().name()), actions(sheet, summary.get()));
    }

    /**
     * The wheel for a character who does not exist yet.
     *
     * <p>Only the two things that do anything. Offering Cast to someone with no class would be
     * offering a button that fails, and a menu that lies is worse than a command that at least says
     * what it wanted.
     */
    private static List<WheelOption> creation() {
        List<WheelOption> options = new ArrayList<>();
        options.add(new WheelOption(Component.translatable("ddc.wheel.class"),
                Component.translatable("ddc.wheel.class.detail"), Wheels.CLASS_MENU));
        options.add(new WheelOption(Component.translatable("ddc.wheel.race"),
                Component.translatable("ddc.wheel.race.detail"), Wheels.RACE_MENU));
        return options;
    }

    /** The wheel for a character who does. */
    private static List<WheelOption> actions(CharacterSheet sheet, ClassSummary klass) {
        List<WheelOption> options = new ArrayList<>();
        options.add(new WheelOption(Component.translatable("ddc.wheel.roll"),
                Component.literal("1d20"), "roll 1d20"));

        if (klass.canCast()) {
            options.add(new WheelOption(Component.translatable("ddc.wheel.cast"),
                    Component.translatable("ddc.wheel.cast.detail"), Wheels.SPELL_MENU));
        }
        if (klass.has(ClassFeature.Type.SECOND_WIND)) {
            options.add(new WheelOption(Component.translatable("ddc.wheel.second_wind"),
                    Component.translatable("ddc.wheel.second_wind.detail"), "ddc second-wind"));
        }
        if (klass.has(ClassFeature.Type.CHANNEL_DIVINITY)) {
            options.add(new WheelOption(Component.translatable("ddc.wheel.channel"),
                    Component.translatable("ddc.wheel.channel.detail"), "ddc channel-divinity"));
        }
        options.add(new WheelOption(Component.translatable("ddc.wheel.rest"),
                Component.translatable("ddc.wheel.rest.detail"), "ddc rest"));
        options.add(new WheelOption(Component.translatable("ddc.wheel.sheet"),
                Component.empty(), Wheels.SHEET_SCREEN));
        options.add(new WheelOption(Component.translatable("ddc.wheel.race"),
                Component.translatable("ddc.wheel.race.detail"), Wheels.RACE_MENU));
        return options;
    }

    /** The classes a player may pick, as the server's packs define them. */
    public static List<WheelOption> classes() {
        return ClientRules.classes().stream()
                .map(entry -> new WheelOption(Component.literal(entry.name()),
                        Component.translatable("ddc.wheel.pick"), "ddc class " + entry.id()))
                .toList();
    }

    /** The races a player may pick. */
    public static List<WheelOption> races() {
        return ClientRules.races().stream()
                .map(entry -> new WheelOption(Component.literal(entry.name()),
                        Component.translatable("ddc.wheel.pick"), "ddc race " + entry.id()))
                .toList();
    }

    /**
     * The spells this character can cast at what they are looking at.
     *
     * <p>Prepared spells and cantrips: a cantrip is known rather than written down, so it is on the
     * wheel whether or not the book has it. Without a target there is nothing to aim at, and the
     * wheel says so rather than sending a command that cannot work.
     */
    public static List<WheelOption> spells(CharacterSheet sheet) {
        Optional<Entity> target = lookingAt();
        if (target.isEmpty()) {
            return List.of(new WheelOption(Component.translatable("ddc.wheel.no_target"),
                    Component.translatable("ddc.wheel.no_target.detail"), ""));
        }
        String selector = target.get().getUUID().toString();

        List<WheelOption> options = ClientRules.spells().stream()
                .filter(spell -> spell.level() == 0 || sheet.hasPrepared(spell.id()))
                .map(spell -> new WheelOption(
                        Component.literal(spell.name()),
                        spell.level() == 0
                                ? Component.translatable("ddc.wheel.cantrip")
                                : Component.translatable("ddc.wheel.level", spell.level()),
                        "ddc cast " + spell.id() + " " + selector))
                .toList();

        return options.isEmpty()
                ? List.of(new WheelOption(Component.translatable("ddc.wheel.no_spells"),
                        Component.translatable("ddc.wheel.no_spells.detail"), ""))
                : options;
    }

    /** The entity under the crosshair, if the player is looking at one. */
    private static Optional<Entity> lookingAt() {
        Minecraft client = Minecraft.getInstance();
        if (client.crosshairPickEntity != null && client.crosshairPickEntity.isAlive()) {
            return Optional.of(client.crosshairPickEntity);
        }
        return Optional.empty();
    }

    /** A spell's id, tidied for a label, for when the server never told us its name. */
    static String name(Identifier spell) {
        String path = spell.getPath().replace('_', ' ');
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    /** The commands that open another wheel instead of being sent. */
    public static final class Wheels {
        public static final String CLASS_MENU = "@class";
        public static final String RACE_MENU = "@race";
        public static final String SPELL_MENU = "@spell";
        public static final String SHEET_SCREEN = "@sheet";

        private Wheels() {
        }

        /** Whether an option opens something rather than sending a command. */
        public static boolean isMenu(String command) {
            return command.startsWith("@");
        }
    }

    /** Unused ids fall back to this so a stale prepared spell still reads as a name. */
    static Component labelFor(RulesPayload.Entry entry) {
        return Component.literal(entry.name());
    }
}
