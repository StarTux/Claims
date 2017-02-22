package com.winthier.claims;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import org.bukkit.ChatColor;

@AllArgsConstructor
public final class ClaimOption {
    public final String key;
    public final String displayName;
    public final String description;
    public final String defaultValue;

    @AllArgsConstructor
    static class State {
        public final String value;
        public final String displayName;
        public final String description;
        public final ChatColor color;
        public final ChatColor activeColor;
    }

    public final List<State> states;

    public static ClaimOption booleanOption(String key, String displayName, String description, String defaultValue) {
        return new ClaimOption(key, displayName, description, defaultValue, Arrays.asList(
                              new State("true", "On", displayName + " On", ChatColor.DARK_GRAY, ChatColor.GREEN),
                              new State("false", "Off", displayName + " Off", ChatColor.DARK_GRAY, ChatColor.DARK_RED)));
    }

    public static ClaimOption intOption(String key, String displayName, String description, String defaultValue, int min, int max) {
        List<State> states = new ArrayList<>();
        for (int i = min; i <= max; ++i) {
            String str = "" + i;
            states.add(new State(str, str, displayName + " " + str, ChatColor.DARK_GRAY, ChatColor.GREEN));
        }
        return new ClaimOption(key, displayName, description, defaultValue, states);
    }
}
