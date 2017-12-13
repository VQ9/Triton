package com.rexcantor64.multilanguageplugin.commands;

import com.rexcantor64.multilanguageplugin.SpigotMLP;
import com.rexcantor64.multilanguageplugin.web.GistManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class ReloadCMD implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.hasPermission("multilanguageplugin.reload")) {
            s.sendMessage(SpigotMLP.get().getMessage("error.no-permission"));
            return true;
        }

        if (args.length > 1) {
            SpigotMLP main = SpigotMLP.get();
            if (!s.hasPermission("multilanguageplugin.reload.web")) {
                s.sendMessage(main.getMessage("error.no-permission"));
                return true;
            }

            GistManager.HttpResponse response = main.getGistUploader().downloader(args[1]);

            if (response.getStatusCode() == 0) {
                s.sendMessage(main.getMessage("error.web.failed-fetch", main.getMessage("error.web.no-internet", response.getPage())));
                return true;
            }
            if (response.getStatusCode() != 200) {
                s.sendMessage(main.getMessage("error.web.failed-fetch", main.getMessage("error.web.incorrect-status", response.getStatusCode())));
                return true;
            }
            try {
                JSONObject responseJson = new JSONObject(response.getPage());
                JSONObject files = responseJson.getJSONObject("files");
                try {
                    JSONObject configFile = new JSONObject(files.getJSONObject("config.json").getString("content"));
                    FileConfiguration config = main.getConfig();
                    config.set("force-minecraft-locale", configFile.optBoolean("forceLocale", false));
                    config.set("debug", configFile.optBoolean("debug", false));
                    config.set("main-language", configFile.optString("mainLanguage", "en_GB"));
                    JSONObject parser = configFile.optJSONObject("parser");
                    config.set("language-creation.syntax", parser.optString("syntax", "lang"));
                    config.set("language-creation.syntax-args", parser.optString("syntaxArgs", "args"));
                    config.set("language-creation.syntax-arg", parser.optString("syntaxArg", "arg"));
                    config.set("language-creation.enabled.chat-messages", parser.optBoolean("chat", true));
                    config.set("language-creation.enabled.action-bars", parser.optBoolean("actionbar", true));
                    config.set("language-creation.enabled.titles", parser.optBoolean("titles", true));
                    config.set("language-creation.enabled.guis", parser.optBoolean("guis", true));
                    config.set("language-creation.enabled.scoreboards", parser.optBoolean("scoreboards", true));
                    config.set("language-creation.enabled.scoreboards-advanced", parser.optBoolean("scoreboardsAdvanced", true));
                    config.set("language-creation.enabled.holograms", parser.optJSONArray("holograms").toList());
                    config.set("language-creation.enabled.holograms-allow-all", parser.optBoolean("hologramsAllowAll", false));
                    config.set("language-creation.enabled.kick", parser.optBoolean("kick", true));
                    config.set("language-creation.enabled.tab", parser.optBoolean("tab", true));
                    config.set("language-creation.enabled.items", parser.optBoolean("items", true));
                    config.set("language-creation.enabled.inventory-items", parser.optBoolean("inventoryItems", true));
                    config.set("language-creation.enabled.signs", parser.optBoolean("signs", true));

                    config.set("languages", null);
                    ConfigurationSection langSection = config.createSection("languages");

                    JSONArray languages = configFile.optJSONArray("languages");
                    for (int i = 0; i < languages.length(); i++) {
                        JSONObject lang = languages.optJSONObject(i);
                        ConfigurationSection sec = langSection.createSection(lang.optString("name"));
                        sec.set("flag", lang.optString("flag"));
                        sec.set("display-name", lang.optString("display"));
                        sec.set("minecraft-code", lang.optJSONArray("codes").toList());
                    }
                    main.saveConfig();
                    JSONArray languageFile = new JSONArray(files.getJSONObject("language.json").getString("content"));
                    try {
                        FileWriter fileWriter = new FileWriter(new File(SpigotMLP.get().getDataFolder(), "languages.json"));
                        fileWriter.write(languageFile.toString(4));
                        fileWriter.flush();
                    } catch (Exception e) {
                        s.sendMessage(main.getMessage("error.web.failed-file-update", "languages.json", e.getMessage()));
                        return true;
                    }
                } catch (JSONException e) {
                    s.sendMessage(main.getMessage("error.web.failed-fetch", main.getMessage("error.web.files-not-found")));
                    return true;
                }
            } catch (JSONException e) {
                s.sendMessage(main.getMessage("error.web.failed-fetch", main.getMessage("error.web.invalid-response", e.getMessage())));
                return true;
            }
        }

        SpigotMLP.get().reload();
        s.sendMessage(SpigotMLP.get().getMessage("success.reload"));
        return true;
    }

}
