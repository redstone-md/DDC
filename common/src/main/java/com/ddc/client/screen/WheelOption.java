package com.ddc.client.screen;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

/**
 * One slice of the wheel: a label, and the command it runs.
 *
 * <p>A command rather than a packet. The commands already carry every check the server makes, so a
 * wheel that sent its own packets would be a second door into the same rules, needing the same locks
 * fitted twice. This way the wheel is a faster way to type, and nothing more.
 *
 * <p>The labels are components rather than strings so they can be translated. A menu that only
 * speaks English is a menu half the table cannot read.
 *
 * @param label   what the slice says
 * @param detail  a second line, or empty
 * @param command what it sends, without the leading slash; one starting with an at-sign opens
 *                another wheel instead of being sent, and an empty one does nothing
 * @param icon    the picture above the label, or empty for a slice that is only words
 */
@Environment(EnvType.CLIENT)
public record WheelOption(Component label, Component detail, String command,
        java.util.Optional<Icon> icon) {

    public WheelOption(Component label, Component detail, String command) {
        this(label, detail, command, java.util.Optional.empty());
    }

    public WheelOption(Component label, Component detail, String command, Icon icon) {
        this(label, detail, command, java.util.Optional.of(icon));
    }

    public WheelOption {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(icon, "icon");
    }

    public static WheelOption of(Component label, String command) {
        return new WheelOption(label, Component.empty(), command);
    }

    /** Whether this slice is a message rather than an action. */
    public boolean isInert() {
        return command.isEmpty();
    }
}
