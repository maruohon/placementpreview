package fi.dy.masa.placementpreview.config;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import fi.dy.masa.placementpreview.Reference;

public class Configs
{
    public static File configurationFile;
    public static Configuration config;

    public static final String CATEGORY_GENERIC = "Generic";

    @SubscribeEvent
    public void onConfigChangedEvent(OnConfigChangedEvent event)
    {
        if (Reference.MOD_ID.equals(event.getModID()) == true)
        {
            loadConfigs(config);
        }
    }

    public static void loadConfigsFromFile(File configFile)
    {
        configurationFile = configFile;
        config = new Configuration(configFile, null, true);
        config.load();

        loadConfigs(config);
    }

    public static void loadConfigs(Configuration conf)
    {
        Property prop;

        //prop = conf.get(CATEGORY_GENERIC, "coordinateFormatCustomized", false);
        //prop.setComment("Use the customized coordinate format string");
        //coordinateFormatCustomized = prop.getBoolean();

        if (conf.hasChanged() == true)
        {
            conf.save();
        }
    }
}
