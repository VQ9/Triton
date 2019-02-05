package com.rexcantor64.triton.scoreboard;

import net.md_5.bungee.api.chat.BaseComponent;

public class ScoreboardLine {

    String result = "";

    String rawPrefix = "";
    BaseComponent[] rawPrefixComp = new BaseComponent[0];
    String rawEntry = "";
    String rawSuffix = "";
    BaseComponent[] rawSuffixComp = new BaseComponent[0];

    String prefix = "";
    String entry = "";
    String suffix = "";

    Integer score = null;

}
