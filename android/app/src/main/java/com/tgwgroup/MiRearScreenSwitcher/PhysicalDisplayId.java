package com.tgwgroup.MiRearScreenSwitcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PhysicalDisplayId {
    static String fromDump(String dump, int logicalId) {
        Matcher viewports = Pattern.compile("DisplayViewport\\{[^}]*\\}").matcher(dump);
        String found = null;
        while (viewports.find()) {
            String viewport = viewports.group();
            if (!Pattern.compile("\\bdisplayId=" + logicalId + "\\b").matcher(viewport).find()) continue;
            Matcher id = Pattern.compile("\\buniqueId=['\"]local:(\\d+)['\"]").matcher(viewport);
            if (id.find()) {
                if (found != null && !found.equals(id.group(1))) return null;
                found = id.group(1);
            }
        }
        return found;
    }
}
