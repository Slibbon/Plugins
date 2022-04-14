package net.runelite.client.plugins.oneclickvyres;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.queries.BankItemQuery;
import net.runelite.api.queries.GameObjectQuery;
import net.runelite.api.queries.NPCQuery;
import net.runelite.api.queries.WallObjectQuery;
import net.runelite.api.util.Text;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.rs.api.RSClient;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Extension
@PluginDescriptor(
      name = "One Click Vyres",
      description = "Pickpocket vyres and bank for food",
      tags = {"slib","pickpocket","skilling","thieving"},
      enabledByDefault = false
)

@Slf4j
public class OneClickVyresPlugin extends Plugin
{
   @Inject
   private Client client;

   @Inject
   private ItemManager itemManager;

   @Inject
   private OneClickVyresConfig config;

   @Inject
   private Notifier notifier;

   @Inject
   private ChatMessageManager chatMessageManager;

   @Inject
   private ConfigManager configManager;

   @Inject
   private OverlayManager overlayManager;

   @Provides
   OneClickVyresConfig provideConfig(ConfigManager configManager)
   {
      return configManager.getConfig(OneClickVyresConfig.class);
   }

   @Override
   protected void startUp()
   {
      teleCooldown = 0;
      running = true;
   }

   private boolean debug = false;
   private boolean running = true;
   private State state = State.THIEVING;
   private int bankingState = 0;
   private boolean shouldHeal = false;
   private int teleCooldown = 0;

   Set<String> foodMenuOption = Set.of("Drink","Eat");
   Set<Integer> foodBlacklist = Set.of(139,141,143,2434,3024,3026,3028,3030,24774,189,191,193,2450,26340,26342,26344,26346);
   Set<Integer> coinPouches = Set.of(22521,22522,22523,22524,22525,22526,22527,22528,22529,22530,22531,22532,22533,22534,22535,22536,22537,22538,24703);
   Set<Integer> DROP_IDS = Set.of(24774, 1619, 1601);
   Set<Integer> BANK_IDS = Set.of(24777, 560, 565);
   Set<Integer> NPC_VYRES = Set.of(9761, 9759, 9756);
   private final int VYRE_ID = 9712;
   private static final int DODGY_NECKLACE_ID = 21143;

   @Subscribe
   private void onClientTick(ClientTick event)
   {
      if (client.getLocalPlayer() == null || client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen())
         return;
      String text = "<col=00ff00>One Click Vyres";
      client.insertMenuItem(text, "", MenuAction.UNKNOWN.getId(), 0, 0, 0, true);
      client.setTempMenuEntry(Arrays.stream(client.getMenuEntries()).filter(x->x.getOption().equals(text)).findFirst().orElse(null));
   }

   @Subscribe
   public void onMenuOptionClicked(MenuOptionClicked event)
   {
      // Change click to pickpocket
      if (event.getMenuOption().equals("<col=00ff00>One Click Vyres"))
      {
         handleClick(event);
      }
   }

   @Subscribe
   public void onGameTick(GameTick event)
   {
      if (!running) return;
      if (state == State.THIEVING) {
         if (isInHouse() && npcInHouse()) {
            notifier.notify("Vyre attacking in house, teleporting");
            sendGameMessage("Vyre attacking, take it out of the house then restart plugin");
            running = false;
         } else if (isInHouse() && !thieveVyreInHouse()) {
            notifier.notify("Vyre to thieve from not in house");
            sendGameMessage("Bring vyre to thieve from inside then restart plugin");
            running = false;
         }
      }

      if (teleCooldown > 0) teleCooldown--;

      if (client.getBoostedSkillLevel(Skill.HITPOINTS) >= Math.min(client.getRealSkillLevel(Skill.HITPOINTS), config.HPTopThreshold()))
      {
         shouldHeal = false;
      }
      else if (client.getBoostedSkillLevel(Skill.HITPOINTS) <= Math.max(5,config.HPBottomThreshold()))
      {
         shouldHeal = true;
      }
   }

   private void handleClick(MenuOptionClicked event) {
      if (!running) {
         // Tele to POH if aggressive NPC is in the house then the user can sort it out
         if (npcInHouse() && !isInPOH()) {
            event.setMenuEntry(teleToPOH());
         } else {
            event.consume();
         }
         return;
      }
      if (state == State.THIEVING) {
         thieve(event);
      } else if (state == State.BANKING) {
         bank(event);
      }
   }

   private void thieve(MenuOptionClicked event)
   {
      if (!isInHouse()) {
         print("Not in thieving room");
         if (isInEdge()) {
            print("In edgeville, starting with banking");
            state = State.BANKING;
            return;
         }


         if(client.getLocalPlayer().isMoving())
         {
            print("Consuming click while moving");
            event.consume();
            return;
         }

         WallObject closedDoor = new WallObjectQuery().atWorldLocation(new WorldPoint(3607,3323,0)).result(client).first();
         if (closedDoor != null) {
            event.setMenuEntry(createMenuEntry(closedDoor.getId(), MenuAction.GAME_OBJECT_FIRST_OPTION, getLocation(closedDoor).getX(), getLocation(closedDoor).getY(), false));
            return;
         }
         WorldPoint tile = new WorldPoint(3608,3323,0);
         walkTo(tile);
         return;
      }

      WallObject openedDoor = new WallObjectQuery().atWorldLocation(new WorldPoint(3608,3323,0)).result(client).first();
      if (isInHouse() && openedDoor != null) {
         event.setMenuEntry(createMenuEntry(openedDoor.getId(), MenuAction.GAME_OBJECT_FIRST_OPTION, getLocation(openedDoor).getX(), getLocation(openedDoor).getY(), false));
         return;
      }

      NPC npc =  new NPCQuery().idEquals(VYRE_ID).result(client).nearestTo(client.getLocalPlayer());
      if (npc != null)
      {
         event.setMenuEntry(client.createMenuEntry(
                 "Pickpocket",
                 npc.getName(),
                 npc.getIndex(),
                 MenuAction.NPC_THIRD_OPTION.getId(),
                 0,
                 0,
                 false));
      }
      else
      {
         sendGameMessage("Vyre not found");
         event.consume();
         return;
      }

      if (!event.getMenuOption().equals("Pickpocket"))
      {
         return;
      }

      for (int item:DROP_IDS) {
         WidgetItem lastItem = getLastInventoryItem(item);
         // Drop if no space for new drops or item is a pint
         if (lastItem != null && (remainingInventorySlots() < 2 || lastItem.getId() == 24774)) {
            event.setMenuEntry(dropItemMenuEntry(getLastInventoryItem(item)));
            return;
         }
      }

      if(shouldHeal || remainingInventorySlots() < 2)
      {
         boolean hpLow = client.getBoostedSkillLevel(Skill.HITPOINTS) <= Math.max(5,config.HPBottomThreshold());
         WidgetItem food = getItemMenu(foodMenuOption,foodBlacklist);
         if (food == null && hpLow)
         {
            event.consume();
            notifier.notify("You are out of food");
            sendGameMessage("You are out of food");
            state = State.BANKING;
            return;
         }
         else if (food != null)
         {
            String[] foodMenuOptions = itemManager.getItemComposition(food.getId()).getInventoryActions();
            event.setMenuEntry(client.createMenuEntry(
                    foodMenuOptions[0],
                    foodMenuOptions[0],
                    food.getId(),
                    MenuAction.ITEM_FIRST_OPTION.getId(),
                    food.getIndex(),
                    WidgetInfo.INVENTORY.getId(),
                    false));
            return;
         }
      }

      WidgetItem coinpouch = getWidgetItem(coinPouches);
      if (coinpouch != null && coinpouch.getQuantity() == 28)
      {
         event.setMenuEntry(client.createMenuEntry(
                 "Open-all",
                 "Coin Pouch",
                 coinpouch.getId(),
                 MenuAction.ITEM_FIRST_OPTION.getId(),
                 coinpouch.getIndex(),
                 WidgetInfo.INVENTORY.getId(),
                 false));
      }
      //dodgy necklace
//      else if(config.enableNecklace() && getWidgetItem(DODGY_NECKLACE_ID) != null && !isItemEquipped(List.of(DODGY_NECKLACE_ID)))
//      {
//         event.setMenuEntry(client.createMenuEntry(
//                 "Wear",
//                 "Necklace",
//                 DODGY_NECKLACE_ID,
//                 MenuAction.ITEM_SECOND_OPTION.getId(),
//                 getWidgetItem(DODGY_NECKLACE_ID).getIndex(),
//                 WidgetInfo.INVENTORY.getId(), false));
//      }
      //varbit is shadowveil cooldown
      else if(config.enableSpell() && client.getVarbitValue(12414) == 0)
      {
         //check spellbook
         if(client.getVarbitValue(4070) != 3)
         {
            event.consume();
            notifier.notify("You are on the wrong spellbook");
            sendGameMessage("You are on the wrong spellbook");
         }
         else if(client.getBoostedSkillLevel(Skill.MAGIC) >= 47)
         {
            event.setMenuEntry(client.createMenuEntry(
                    "Cast",
                    "Shadow Veil",
                    1,
                    MenuAction.CC_OP.getId(),
                    -1,
                    WidgetInfo.SPELL_SHADOW_VEIL.getId(),
                    false));
         } 
         else 
         {
            event.consume();
            notifier.notify("Magic level too low to cast this spell!");
            sendGameMessage("Magic level too low to cast this spell!");
         }
      }
   }

   private void bank(MenuOptionClicked event) {
      if(client.getLocalPlayer().isMoving())
      {
         print("consuming click while moving");
         event.consume();
         return;
      }

      if (bankingState == 0) {
         if (isInEdge()) {
            bankingState = 1;
            return;
         }
         if (isInPOH()) {
            // Heal if HP low
            if (client.getBoostedSkillLevel(Skill.HITPOINTS) < client.getRealSkillLevel(Skill.HITPOINTS)) {
               event.setMenuEntry(drinkFromPool());
               return;
            }
            GameObject jeweleryBox = getGameObject(29156);
            if (jeweleryBox != null) {
               event.setMenuEntry(createMenuEntry(jeweleryBox.getId(), MenuAction.GAME_OBJECT_THIRD_OPTION, getLocation(jeweleryBox).getX(), getLocation(jeweleryBox).getY(), false));
               return;
            } else {
               print("No jew box");
            }
         } else if (!isInPOH()) {
            if (teleCooldown <= 0) {
               event.setMenuEntry(teleToPOH());
               teleCooldown = 5;
            }
            return;
         }
      }
      else if (bankingState == 1) {
         if (!bankOpen()) {
            print("Bank not open");
            GameObject bankBooth = getGameObject(10355);
            if (bankBooth != null) {
               event.setMenuEntry(createMenuEntry(bankBooth.getId(), MenuAction.GAME_OBJECT_SECOND_OPTION, getLocation(bankBooth).getX(), getLocation(bankBooth).getY(), false));
               return;
            }
         } else if (bankOpen()) {
            if (itemsToDeposit()) {
               print("Deposit");
               event.setMenuEntry(depositAll());
            } else if (!hasFood()) {
               event.setMenuEntry(withdrawFood());
               print("Withdraw food");
            } else if (hasFood() && remainingInventorySlots() < 2) {
               print("Deposit extra");
               event.setMenuEntry(depositExtraFood());
            } else if (hasFood() && remainingInventorySlots() >= 2) {
               bankingState = 2;
            }
            return;
         }
      } else if (bankingState == 2) {
         print("Tele to darkmeyer");
         if (isInDarkmeyer()) {
            resetBanking();
            state = State.THIEVING;
            return;
         }
         if (teleCooldown <= 0) {
            MenuEntry task = teleToDarkmeyer();
            if (task != null) event.setMenuEntry(task);
            teleCooldown = 5;
            return;
         }
      }
   }

   private void walkTo(WorldPoint point) {
      int x = point.getX() - client.getBaseX();
      int y = point.getY() - client.getBaseY();
      RSClient rsClient = (RSClient) client;
      rsClient.setSelectedSceneTileX(x);
      rsClient.setSelectedSceneTileY(y);
      rsClient.setViewportWalking(true);
      rsClient.setCheckClick(false);
   }

   private void resetBanking() {
      bankingState = 0;
   }

   private MenuEntry teleToDarkmeyer() {
      WidgetItem amulet = getInventoryItem(22400);
      if (amulet != null) {
         return createMenuEntry(amulet.getId(), MenuAction.ITEM_THIRD_OPTION, amulet.getIndex(), WidgetInfo.INVENTORY.getId(), false);
      }

//      if (client.getItemContainer(InventoryID.EQUIPMENT) != null && client.getItemContainer(InventoryID.EQUIPMENT).contains(22400)) {
//         return createMenuEntry(3, MenuAction.CC_OP, -1, 22400, false);
//      }
      return null;
   }

   private MenuEntry depositAll(){
      for (int id:DROP_IDS) {
         if (getInventoryItem(id) != null) {
            return createMenuEntry(
                    8,
                    MenuAction.CC_OP_LOW_PRIORITY,
                    getInventoryItem(id).getIndex(),
                    983043,
                    false);
         }
      }
      for (int id:BANK_IDS) {
         if (getInventoryItem(id) != null) {
            return createMenuEntry(
                    8,
                    MenuAction.CC_OP_LOW_PRIORITY,
                    getInventoryItem(id).getIndex(),
                    983043,
                    false);
         }
      }
      return null;
   }

   private MenuEntry depositExtraFood() {
      if (getInventoryItem(config.food().getId()) != null) {
         return createMenuEntry(
              2,
              MenuAction.CC_OP_LOW_PRIORITY,
              getInventoryItem(config.food().getId()).getIndex(),
              983043,
              false);
      }
      return null;
   }

   private MenuEntry withdrawFood() {
      return createMenuEntry(7, MenuAction.CC_OP_LOW_PRIORITY, getBankIndex(config.food().getId()), WidgetInfo.BANK_ITEM_CONTAINER.getId(), false);
   }

   private int getBankIndex(int ID){
      WidgetItem bankItem = new BankItemQuery()
              .idEquals(ID)
              .result(client)
              .first();
      return bankItem.getWidget().getIndex();
   }

   private boolean hasFood() {
      return getInventoryItem(config.food().getId()) != null;
   }

   private boolean itemsToDeposit() {
      for (int id:DROP_IDS) {
         if (getInventoryItem(id) != null) return true;
      }
      for (int id:BANK_IDS) {
         if (getInventoryItem(id) != null) return true;
      }
      return false;
   }

   private boolean bankOpen() {
      return client.getItemContainer(InventoryID.BANK) != null;
   }

   private boolean isInEdge() {
      return client.getLocalPlayer().getWorldLocation().isInArea(new WorldArea(new WorldPoint(3085,3488,0),new WorldPoint(3100,3500,0)));
   }

   private boolean isInDarkmeyer() {
      return client.getLocalPlayer().getWorldLocation().isInArea(new WorldArea(new WorldPoint(3589,3334,0),new WorldPoint(3595,3340,0)));
   }

   private boolean npcInHouse() {
      NPC npc = new NPCQuery().idEquals(NPC_VYRES).result(client).nearestTo(client.getLocalPlayer());
      if (npc != null && npc.getWorldLocation().isInArea(new WorldArea(new WorldPoint(3608,3322,0),new WorldPoint(3613,3328,0)))) {
         return true;
      }
      return false;
   }

   private boolean thieveVyreInHouse() {
      NPC npc = new NPCQuery().idEquals(VYRE_ID).result(client).nearestTo(client.getLocalPlayer());
      if (npc != null && npc.getWorldLocation().isInArea(new WorldArea(new WorldPoint(3608,3322,0),new WorldPoint(3613,3328,0)))) {
         return true;
      }
      return false;
   }

   private boolean isInHouse() {
      return client.getLocalPlayer().getWorldLocation().isInArea(new WorldArea(new WorldPoint(3608,3322,0),new WorldPoint(3613,3328,0)));
   }

   private MenuEntry drinkFromPool() {
      GameObject pool = getGameObject(29241);
      if (pool == null) pool = getGameObject(40848); // Frozen pool
      return createMenuEntry(pool.getId(), MenuAction.GAME_OBJECT_FIRST_OPTION, getLocation(pool).getX(),getLocation(pool).getY(), false);
   }

   private Point getLocation(TileObject tileObject) {
      if (tileObject == null) {
         return new Point(0, 0);
      }
      if (tileObject instanceof GameObject) {
         return ((GameObject) tileObject).getSceneMinLocation();
      }
      return new Point(tileObject.getLocalLocation().getSceneX(), tileObject.getLocalLocation().getSceneY());
   }

   private boolean isInPOH() {
      return getGameObject(4525) != null; //checks for portal, p sure this is same for everyone if not need to do alternative check.
   }

   private GameObject getGameObject(int ID) {
      return new GameObjectQuery()
              .idEquals(ID)
              .result(client)
              .nearestTo(client.getLocalPlayer());
   }

   private int remainingInventorySlots() {
      Collection<WidgetItem> items = client.getWidget(WidgetInfo.INVENTORY).getWidgetItems();
      if (items != null) {
         return (28 - items.size());
      }
      return -1;
   }

   private WidgetItem getLastInventoryItem(int id) {
      Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
      if (inventoryWidget != null) {
         Collection<WidgetItem> items = inventoryWidget.getWidgetItems();
         int LastIndex = -1;
         WidgetItem LastItem = null;
         for (WidgetItem item : items) {
            if (item.getId() == id) {
               if (item.getIndex()>LastIndex) {
                  LastIndex = item.getIndex();
                  LastItem = item;
               }
            }
         }
         return LastItem;
      }
      return null;
   }

   private WidgetItem getInventoryItem(int id) {
      Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
      if (inventoryWidget != null) {
         Collection<WidgetItem> items = inventoryWidget.getWidgetItems();
         for (WidgetItem item : items) {
            if (item.getId() == id) {
               return item;
            }
         }
      }
      return null;
   }

   private MenuEntry dropItemMenuEntry(WidgetItem item){
      return createMenuEntry(
              item.getId(),
              MenuAction.ITEM_FIFTH_OPTION,
              item.getIndex(),
              9764864,
              false);
   }

   private List<String> getActions(NPC npc) {
      return Arrays.stream(npc.getComposition().getActions()).map(o -> o == null ? null : Text.removeTags(o)).collect(Collectors.toList());
   }

   private MenuEntry teleToPOH() {
      WidgetItem tab = getInventoryItem(ItemID.TELEPORT_TO_HOUSE);
      WidgetItem conCape = getInventoryItem(ItemID.CONSTRUCT_CAPE);
      WidgetItem conCapeT = getInventoryItem(ItemID.CONSTRUCT_CAPET);

      if (conCape!=null)
      {
         return createMenuEntry(conCape.getId(), MenuAction.ITEM_FOURTH_OPTION, conCape.getIndex(), WidgetInfo.INVENTORY.getId(), false);
      }
      if (conCapeT!=null)
      {
         return createMenuEntry(conCapeT.getId(), MenuAction.ITEM_FOURTH_OPTION, conCapeT.getIndex(), WidgetInfo.INVENTORY.getId(), false);
      }
      if (tab!=null)
      {
         return createMenuEntry(tab.getId(), MenuAction.ITEM_FIRST_OPTION, tab.getIndex(), WidgetInfo.INVENTORY.getId(), false);
      }
      if (client.getItemContainer(InventoryID.EQUIPMENT)!=null)
      {
         if (client.getItemContainer(InventoryID.EQUIPMENT).contains(ItemID.MAX_CAPE) || client.getItemContainer(InventoryID.EQUIPMENT).contains(ItemID.MAX_CAPE_13342))
         {
            return createMenuEntry(5, MenuAction.CC_OP, -1, WidgetInfo.EQUIPMENT_CAPE.getId(), false);
         }
         if (client.getItemContainer(InventoryID.EQUIPMENT).contains(ItemID.CONSTRUCT_CAPE) || client.getItemContainer(InventoryID.EQUIPMENT).contains(ItemID.CONSTRUCT_CAPET))
         {
            return createMenuEntry( 4, MenuAction.CC_OP, -1, WidgetInfo.EQUIPMENT_CAPE.getId(), false);
         }
      }
      return createMenuEntry(1, MenuAction.CC_OP, -1, WidgetInfo.SPELL_TELEPORT_TO_HOUSE.getId(), false);
   }

   public boolean isItemEquipped(Collection<Integer> itemIds) {
      assert client.isClientThread();

      ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);
      if (equipmentContainer != null) {
         Item[] items = equipmentContainer.getItems();
         for (Item item : items) {
            if (itemIds.contains(item.getId())) {
               return true;
            }
         }
      }
      return false;
   }

   public WidgetItem getWidgetItem(Collection<Integer> ids) {
      Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
      if (inventoryWidget != null) {
         Collection<WidgetItem> items = inventoryWidget.getWidgetItems();
         for (WidgetItem item : items) {
            if (ids.contains(item.getId())) {
               return item;
            }
         }
      }
      return null;
   }

   private WidgetItem getWidgetItem(int id) {
      return getWidgetItem(Collections.singletonList(id));
   }

   private WidgetItem getItemMenu(Collection<String>menuOptions, Collection<Integer> ignoreIDs) {
      Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
      if (inventoryWidget != null) {
         Collection<WidgetItem> items = inventoryWidget.getWidgetItems();
         for (WidgetItem item : items) {
            if (ignoreIDs.contains(item.getId())) {
               continue;
            }
            String[] menuActions = itemManager.getItemComposition(item.getId()).getInventoryActions();
            for (String action : menuActions) {
               if (action != null && menuOptions.contains(action)) {
                  return item;
               }
            }
         }
      }
      return null;
   }

   private void sendGameMessage(String message) {
      String chatMessage = new ChatMessageBuilder()
              .append(ChatColorType.HIGHLIGHT)
              .append(message)
              .build();

      chatMessageManager
              .queue(QueuedMessage.builder()
                      .type(ChatMessageType.CONSOLE)
                      .runeLiteFormattedMessage(chatMessage)
                      .build());
   }

   private MenuEntry createMenuEntry(int identifier, MenuAction type, int param0, int param1, boolean forceLeftClick) {
      return client.createMenuEntry(0).setOption("").setTarget("").setIdentifier(identifier).setType(type)
              .setParam0(param0).setParam1(param1).setForceLeftClick(forceLeftClick);
   }

   private void print(String string) //used for debugging, puts a message to the in game chat.
   {
      if (debug)
      {
         client.addChatMessage(ChatMessageType.GAMEMESSAGE,"",string,"");
      }
   }
}
