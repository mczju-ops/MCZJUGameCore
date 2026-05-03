package com.github.mczjuops.mczjugamecore.game.room.menu;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class GameRoomSettingMenu extends Menu {

    private final Map<PlayerExt, AbstractGameRoom> playerEditingMap = new HashMap<>();

    @Override
    public void click(@NotNull InventoryClickEvent event) {
        // 下面的代码要优化一下，有点史
        Player whoClicked = (Player) event.getWhoClicked();
        PlayerExt player = new PlayerExt(whoClicked);

        AbstractGameRoom gameRoom = playerEditingMap.get(player);
        assert gameRoom != null; // 既然能打开这个menu，那显然正在编辑的房间不可能为空
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null) return;
        Component nameComp = Objects.requireNonNull(currentItem.getItemMeta().displayName());
        String fieldName = PlainTextComponentSerializer.plainText().serialize(nameComp);
        if (currentItem.getType() == Material.COMPASS) {
            // 如果是坐标类型的数据
            player.player().closeInventory();
            player.selectLocation(new Consumer<Location>() {
                @Override
                public void accept(Location location) {
                    gameRoom.setField(fieldName, location);
                    player.sender().success(STR."设置坐标成功: \{location.x()}, \{location.y()}, \{location.z()}");
                    gameRoom.setModified(true);
                }
            });
        }else {
            player.player().closeInventory();
            // 如果是字符串或int、float等
            Class<?> type = gameRoom.getFieldType(fieldName);
            ConversationFactory factory = new ConversationFactory(MCZJUGameCore.getInstance())
                    .withFirstPrompt(new StringPrompt() {
                        @Override
                        public @NotNull String getPromptText(@NotNull ConversationContext context) {
                            return STR."请在聊天栏输入字段\{fieldName}的值: ";
                        }

                        @Override
                        public Prompt acceptInput(@NotNull ConversationContext context, String input) {
                            Object value = convert(input, type);
                            gameRoom.setField(fieldName, value);
                            // 没成功会直接报错，后面或许要改
                            player.sender().success(STR."设置字段值成功: \{fieldName} -> \{input}");
                            gameRoom.setModified(true);
                            return Prompt.END_OF_CONVERSATION;
                        }
                    })
                    .withLocalEcho(false); // 不要在聊天框重复显示玩家输入的东西

            factory.buildConversation(player.player()).begin();
        }

    }

    @Override
    public void open(@NotNull PlayerExt player, @NotNull Inventory inventory, Object... args) {
        if (args.length != 2) {
            player.sender().error("参数错误，未传入游戏名+地图名");
            player.player().closeInventory();
            return;
        }
        String gameName = (String)args[0];
        String mapName = (String)args[1];
        AbstractGameRoom gameRoom;
        if (playerEditingMap.get(player) == null){
            // 看有没有这个名字的图
            gameRoom = MCZJUGameCore.getGameRoomManager().getGameRoom(gameName, mapName);
            if (gameRoom == null){
                // 没有这个名字的图，创建一个
                gameRoom = MCZJUGameCore.getGameRoomManager().createGameRoom(gameName, mapName);
            }
        }else {
            gameRoom = playerEditingMap.get(player);
            if (!Objects.equals(gameRoom.getRoomName(), mapName)){
                // 如果和上一张在编辑的地图不同，则不允许直接编辑（后面要修改这个逻辑，先暂时这样写）
                player.sender().error("未退出上一张地图的编辑，请先退出编辑状态，再创建新的地图");
                player.player().closeInventory();
                return;
            }
        }
        if (gameRoom == null){
            player.sender().error("无法创建游戏房间：请检查游戏名是否正确，或参数顺序是否正确");
            return;
        }
        playerEditingMap.put(player, gameRoom); // 标记玩家在编辑这个房间
        Map<String, Class<?>> allFields = gameRoom.getAllFields();
        // TODO 字段太多时，需要做翻页功能
        allFields.forEach((name, type) ->{
            Material m;
            if (Location.class.isAssignableFrom(type)){
                m = Material.COMPASS;
            }else {
                m = Material.BOOK;
            }

            // 设置描述信息
            ItemStack itemStack = new ItemStack(m);
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(TextParser.parse(name));
            ArrayList<Component> lore = new ArrayList<>();
            lore.add(TextParser.parse(STR."类型：\{type.getName()}"));
            itemMeta.lore(lore);
            itemStack.setItemMeta(itemMeta);
            inventory.addItem(itemStack);
        });
    }

    private Object convert(String input, Class<?> type) {
        try {
            if (type == int.class || type == Integer.class) {
                return Integer.valueOf(input);
            } else if (type == float.class || type == Float.class) {
                return Float.valueOf(input);
            } else if (type == double.class || type == Double.class) {
                return Double.valueOf(input);
            } else if (type == long.class || type == Long.class) {
                return Long.valueOf(input);
            } else if (type == boolean.class || type == Boolean.class) {
                return Boolean.valueOf(input);
            } else if (type == String.class) {
                return input;
            } else {
                throw new IllegalArgumentException(STR."不支持的类型: \{type}");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(STR."输入格式错误: \{input}");
        }
    }
}
