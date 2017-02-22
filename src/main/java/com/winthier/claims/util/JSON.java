package com.winthier.claims.util;

import java.util.HashMap;
import java.util.Map;

public final class JSON {
    private JSON() { }

    public static Object commandButton(String label, String tooltip, String command, boolean run) {
        label = Strings.format(label);
        tooltip = Strings.format(tooltip);
        command = Strings.format(command);
        Map<String, Object> result = new HashMap<>();
        result.put("text", label);
        Map<String, String> tooltipMap = new HashMap<>();
        Map<String, String> commandMap = new HashMap<>();
        result.put("hoverEvent", tooltipMap);
        result.put("clickEvent", commandMap);
        tooltipMap.put("action", "show_text");
        tooltipMap.put("value", tooltip);
        commandMap.put("action", run ? "run_command" : "suggest_command");
        commandMap.put("value", command);
        return result;
    }

    public static Object commandRunButton(String label, String tooltip, String command) {
        return commandButton(label, tooltip, command, true);
    }

    public static Object commandSuggestButton(String label, String tooltip, String command) {
        return commandButton(label, tooltip, command, false);
    }
}
