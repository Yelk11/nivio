package de.bonndan.nivio.util;

import com.lowagie.text.html.WebColors;
import org.springframework.util.StringUtils;

public class Color {

    public static String DARK = "111111";
    public static String GRAY = "ccccc";

    /**
     * https://stackoverflow.com/questions/2464745/compute-hex-color-code-for-an-arbitrary-string
     *
     * @param name of a group etc
     * @return a hex color
     */
    public static String nameToRGB(String name) {
        return nameToRGB(name, "FFFFFF");
    }

    public static String nameToRGB(String name, String defaultColor) {
        if (StringUtils.isEmpty(name))
            return defaultColor;

        return String.format("%X", name.hashCode()).concat("000000").substring(0, 6);
    }

    public static String lighten(String color) {
        java.awt.Color col = WebColors.getRGBColor("#" + color).brighter();
        return Integer.toHexString(col.getRGB());
    }
}
