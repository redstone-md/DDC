package com.ddc.client.screen;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * One slice of the wheel: a label, and the command it runs.
 *
 * <p>A command rather than a packet. The commands already carry every check the server makes, so a
 * wheel that sent its own packets would be a second door into the same rules, needing the same locks
 * fitted twice. This way the wheel is a faster way to type, and nothing more.
 *
 * @param label   what the slice says
 * @param detail  a second line, or empty
 * @param command what it sends, without the leading slash
 */
@Environment(EnvType.CLIENT)
public record WheelOption(String label, String detail, String command) {

    public WheelOption {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(command, "command");
    }

    public static WheelOption of(String label, String command) {
        return new WheelOption(label, "", command);
    }
}
