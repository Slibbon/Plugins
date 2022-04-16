package net.runelite.client.plugins.oneclickvyres;

import lombok.Getter;
import net.runelite.api.ItemID;

@Getter
public enum FoodTypes {
    KARAMBWAN(ItemID.COOKED_KARAMBWAN),
    SHARK(ItemID.SHARK),
    MONKFISH(ItemID.MONKFISH),
    SWORDFISH(ItemID.SWORDFISH),
    LOBSTER(ItemID.LOBSTER),
    TUNA(ItemID.TUNA);

    private final int id;

    FoodTypes(int id) {
        this.id = id;
    }
}
