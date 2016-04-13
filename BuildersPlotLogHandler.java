package me.firefly.BuildersPlot;

import java.util.logging.Logger;
import org.bukkit.plugin.PluginDescriptionFile;

public class BuildersPlotLogHandler {
    private BuildersPlot plugin;
    private Logger logger;

    public BuildersPlotLogHandler(BuildersPlot plugin) {
        this.plugin = plugin;
        this.logger = Logger.getLogger("Minecraft");
    }

    private String buildString(String message) {
        PluginDescriptionFile pdFile = this.plugin.getDescription();

        return pdFile.getName() + " " + pdFile.getVersion() + ": " + message;
    }

    public void info(String message) {
        this.logger.info(buildString(message));
    }

    public void warn(String message) {
        this.logger.warning(buildString(message));
    }
}
