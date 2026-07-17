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

    /**
      * The wheel a player sees when they press the key.
      *
      * <p>A Game Master holding the wand gets theirs: PRD 3.2's radial menu, which is what the wand
      * is for. Holding the wand is the ask -- a GM who is playing a character presses the same key and
      * gets their character.
      */
    public static WheelScreen forPlayer(CharacterSheet sheet, Optional<ClassSummary> summary) {
        if (ClientRules.isGameMaster() && isHoldingWand()) {
            return new WheelScreen(Component.translatable("ddc.wheel.encounter"), encounters());
        }
        if (sheet == null || !sheet.hasClass() || summary.isEmpty()) {
            return new WheelScreen(Component.translatable("ddc.wheel.create"), creation());
        }
        return new WheelScreen(Component.literal(summary.get().name()), actions(sheet, summary.get()));
    }

    /**
     * The encounters a Game Master can put on their wand, and the panel.
     *
     * <p>Picking one only selects it; the wand places it where the GM points. Choosing a fight and
     * choosing where it happens are two decisions, and a menu that did both at once would drop a
     * patrol wherever the GM happened to be standing.
     */
    private static List<WheelOption> encounters() {
        List<WheelOption> options = new ArrayList<>(ClientRules.encounters().stream()
                .map(entry -> new WheelOption(
                        Component.literal(entry.name()),
                        Component.translatable("ddc.wheel.mobs", entry.level()),
                        "ddc encounter " + entry.id(), Icon.ENCOUNTER))
                .toList());
        options.add(new WheelOption(Component.translatable("ddc.wheel.gm"),
                Component.translatable("ddc.wheel.gm.detail"), Wheels.GM_PANEL, Icon.GM));
        return options;
    }

    /** Whether the player is holding the wand in either hand. */
    private static boolean isHoldingWand() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }
        return client.player.getMainHandItem().is(com.ddc.registry.DDCItems.GM_WAND.get())
                || client.player.getOffhandItem().is(com.ddc.registry.DDCItems.GM_WAND.get());
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
                Component.translatable("ddc.wheel.class.detail"), Wheels.CLASS_MENU, Icon.CLASS));
        options.add(new WheelOption(Component.translatable("ddc.wheel.race"),
                Component.translatable("ddc.wheel.race.detail"), Wheels.RACE_MENU, Icon.RACE));
        options.add(guide());
        return options;
    }

    /** The wheel for a character who does. */
    private static List<WheelOption> actions(CharacterSheet sheet, ClassSummary klass) {
        List<WheelOption> options = new ArrayList<>();
        options.add(roll());

        if (klass.canCast()) {
            options.add(new WheelOption(Component.translatable("ddc.wheel.cast"),
                    Component.translatable("ddc.wheel.cast.detail"), Wheels.SPELL_MENU, Icon.CAST));
        }
        if (klass.has(ClassFeature.Type.SECOND_WIND)) {
            options.add(new WheelOption(Component.translatable("ddc.wheel.second_wind"),
                    Component.translatable("ddc.wheel.second_wind.detail"), "ddc second-wind", Icon.SECOND_WIND));
        }
        if (klass.has(ClassFeature.Type.ACTION_SURGE)) {
            options.add(new WheelOption(Component.translatable("ddc.wheel.action_surge"),
                    Component.translatable("ddc.wheel.action_surge.detail"), "ddc action-surge", Icon.ACTION_SURGE));
        }
        if (klass.has(ClassFeature.Type.COMBAT_SUPERIORITY)) {
            options.add(new WheelOption(Component.translatable("ddc.wheel.maneuver"),
                    Component.translatable("ddc.wheel.maneuver.detail"), Wheels.MANEUVER_MENU, Icon.MANEUVER));
        }
        if (klass.has(ClassFeature.Type.CHANNEL_DIVINITY)) {
            options.add(new WheelOption(Component.translatable("ddc.wheel.channel"),
                    Component.translatable("ddc.wheel.channel.detail"), Wheels.CHANNEL_MENU,
                    Icon.CHANNEL_DIVINITY));
        }
        options.add(new WheelOption(Component.translatable("ddc.wheel.rest"),
                Component.translatable("ddc.wheel.rest.detail"), "ddc rest", Icon.REST));
        options.add(new WheelOption(Component.translatable("ddc.wheel.sheet"),
                Component.empty(), Wheels.SHEET_SCREEN, Icon.SHEET));
        options.add(new WheelOption(Component.translatable("ddc.wheel.race"),
                Component.translatable("ddc.wheel.race.detail"), Wheels.RACE_MENU, Icon.RACE));
        options.add(guide());
        return options;
    }

    /**
     * The manoeuvres a fighter can spend a die on, aimed at what they are looking at.
     *
     * <p>Like spells, these need a target, and like spells the wheel supplies it: a fighter should
     * not have to type a UUID to trip somebody.
     */
    public static List<WheelOption> maneuvers() {
        Optional<Entity> target = lookingAt();
        if (target.isEmpty()) {
            return List.of(new WheelOption(Component.translatable("ddc.wheel.no_target"),
                    Component.translatable("ddc.wheel.no_target.detail"), ""));
        }
        String selector = target.get().getUUID().toString();
        return java.util.Arrays.stream(com.ddc.character.Maneuver.values())
                .map(maneuver -> new WheelOption(
                        Component.translatable("ddc.maneuver." + maneuver.id()),
                        Component.translatable("ddc.maneuver." + maneuver.id() + ".detail"),
                        "ddc maneuver " + maneuver.id() + " " + selector, Icon.MANEUVER))
                .toList();
    }

    /**
     * The roll, carrying whatever the streamer's chat voted for.
     *
     * <p>PRD 4.5 promises viewers can vote the streamer advantage or disadvantage, and the tally was
     * counted and then thrown away: nothing read it. It rides on the roll now.
     *
     * <p>This is the client choosing a roll mode, which is allowed for exactly one reason: it is the
     * same choice the streamer could make by typing {@code /roll 1d20 advantage} themselves. The vote
     * is their chat, their stream, their decision to let it in. It is not a rule the server relaxes.
     */
    private static WheelOption roll() {
        com.ddc.core.dice.RollMode mode = com.ddc.client.DDCClient.vote().mode();
        String command = mode == com.ddc.core.dice.RollMode.NORMAL
                ? "roll 1d20"
                : "roll 1d20 " + mode.name().toLowerCase(java.util.Locale.ROOT);
        Component detail = mode == com.ddc.core.dice.RollMode.NORMAL
                ? Component.literal("1d20")
                : Component.translatable("ddc.wheel.roll.voted",
                        Component.translatable("ddc.roll.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)));
        return new WheelOption(Component.translatable("ddc.wheel.roll"), detail, command, Icon.ROLL);
    }

    /** The way out of not knowing. On every wheel, because that is where someone lost will look. */
    private static WheelOption guide() {
        return new WheelOption(Component.translatable("ddc.wheel.guide"),
                Component.translatable("ddc.wheel.guide.detail"), Wheels.GUIDE_SCREEN, Icon.GUIDE);
    }

    /**
     * The three things a cleric can spend their channel on.
     *
     * <p>A submenu rather than three slices on the main wheel: they are one feature, spent once, and
     * a wheel that offered them side by side would read as three separate things a cleric has.
     */
    public static List<WheelOption> channel() {
        return List.of(
                new WheelOption(Component.translatable("ddc.divinity.turn"),
                        Component.translatable("ddc.divinity.turn.detail"),
                        "ddc channel-divinity turn", Icon.CHANNEL_DIVINITY),
                new WheelOption(Component.translatable("ddc.divinity.heal"),
                        Component.translatable("ddc.divinity.heal.detail"),
                        "ddc channel-divinity heal", Icon.SECOND_WIND),
                new WheelOption(Component.translatable("ddc.divinity.bless"),
                        Component.translatable("ddc.divinity.bless.detail"),
                        "ddc channel-divinity bless", Icon.CAST));
    }

    /** The classes a player may pick, as the server's packs define them. */
    public static List<WheelOption> classes() {
        return ClientRules.classes().stream()
                .map(entry -> new WheelOption(Component.literal(entry.name()),
                        Component.translatable("ddc.wheel.pick"), "ddc class " + entry.id(), Icon.CLASS))
                .toList();
    }

    /** The races a player may pick. */
    public static List<WheelOption> races() {
        return ClientRules.races().stream()
                .map(entry -> new WheelOption(Component.literal(entry.name()),
                        Component.translatable("ddc.wheel.pick"), "ddc race " + entry.id(), Icon.RACE))
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
                        "ddc cast " + spell.id() + " " + selector, Icon.CAST))
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
        public static final String MANEUVER_MENU = "@maneuver";
        public static final String GUIDE_SCREEN = "@guide";
        public static final String CHANNEL_MENU = "@channel";
        public static final String GM_PANEL = "@gm";

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
