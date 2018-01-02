package com.rexcantor64.multilanguageplugin.utils;

import com.rexcantor64.multilanguageplugin.config.interfaces.Configuration;

import java.util.List;

public class YAMLUtils {

    public static List<String> getStringOrStringList(Configuration config, String index) {
        List<String> result = config.getStringList(index);
        if (result.size() == 0)
            if (config.getString(index) != null) result.add(config.getString(index));
        return result;
    }

}
