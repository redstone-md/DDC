package com.ddc.client.stream;

import com.ddc.core.dice.DieRoll;
import com.ddc.core.dice.RollResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The JSON the stream overlay is fed, from ARCHITECTURE.md 5.
 *
 * <p>The shape is the document's, so a widget written against it works:
 *
 * <pre>{@code
 * {
 *   "event": "dice_roll",
 *   "data": {
 *     "player": "StreamerName", "die_type": "d20", "natural_roll": 20,
 *     "modifier": 4, "total": 24, "is_critical_success": true
 *   }
 * }
 * }</pre>
 *
 * <p>Built by hand rather than reflected off the record: this is a public contract that a streamer's
 * HTML depends on, and it should change when someone decides to change it, not because a field was
 * renamed.
 */
@Environment(EnvType.CLIENT)
public final class OverlayEvents {

    private OverlayEvents() {
    }

    /** A roll, as the overlay sees it. */
    public static String diceRoll(String player, RollResult result) {
        JsonObject data = new JsonObject();
        data.addProperty("player", player);
        data.addProperty("notation", result.expression().toString());
        data.addProperty("die_type", result.rolls().getFirst().die().toString());
        data.addProperty("modifier", result.modifier());
        data.addProperty("total", result.total());
        data.addProperty("is_critical_success", result.isNatural20());
        data.addProperty("is_critical_failure", result.isNatural1());
        data.addProperty("mode", result.mode().name().toLowerCase(java.util.Locale.ROOT));

        result.naturalD20().map(DieRoll::value)
                .ifPresent(natural -> data.addProperty("natural_roll", natural));

        JsonArray faces = new JsonArray();
        result.rolls().forEach(roll -> {
            JsonObject face = new JsonObject();
            face.addProperty("die", roll.die().toString());
            face.addProperty("value", roll.value());
            face.addProperty("counted", roll.isKept());
            faces.add(face);
        });
        data.add("dice", faces);

        return event("dice_roll", data);
    }

    /** The character sheet, so a widget can draw a party card. */
    public static String characterSheet(String player, String className, int level, int hitPoints,
            int maxHitPoints) {
        JsonObject data = new JsonObject();
        data.addProperty("player", player);
        data.addProperty("class", className);
        data.addProperty("level", level);
        data.addProperty("hit_points", hitPoints);
        data.addProperty("max_hit_points", maxHitPoints);
        return event("character_sheet", data);
    }

    private static String event(String name, JsonObject data) {
        JsonObject root = new JsonObject();
        root.addProperty("event", name);
        root.add("data", data);
        return root.toString();
    }
}
