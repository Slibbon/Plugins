package net.runelite.client.plugins.oneclickvyres;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("oneclickvyres")
public interface OneClickVyresConfig extends Config
{
    @ConfigItem(
            keyName = "food",
            name = "Food Type",
            description = "Food to eat",
            position = 0
    )
    default FoodTypes food() {
        return FoodTypes.KARAMBWAN;
    }

    @Range(
            max = 99,
            min = 10
    )
    @ConfigItem(
            keyName = "HPTopThreshold",
            name = "Maximum HP",
            description = "You will STOP eating when your HP is at or above this number",
            position = 1
    )
    default int HPTopThreshold()
    {
        return 80;
    }

    @Range(
            max = 99,
            min = 10
    )
    @ConfigItem(
            keyName = "HPBottomThreshold",
            name = "Minimum HP",
            description = "You will START eating when your HP is at or below this number",
            position = 2
    )
    default int HPBottomThreshold()
    {
        return 10;
    }

    @ConfigItem(
            keyName = "enableSpell",
            name = "Use Shadow Veil",
            description = "This will cast the arceuus spell Shadow Veil",
            position = 3
    )
    default boolean enableSpell()
    {
        return false;
    }

//    @ConfigItem(
//            keyName = "enableNecklace",
//            name = "Dodgy Necklace",
//            description = "This will put on dodgy necklaces when they break",
//            position = 4
//    )
//    default boolean enableNecklace()
//    {
//        return false;
//    }
}
