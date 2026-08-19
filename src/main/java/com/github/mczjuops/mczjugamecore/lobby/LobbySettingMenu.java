package com.github.mczjuops.mczjugamecore.lobby;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.menu.Menu;
import com.github.mczjuops.mczjugamecore.utils.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.Range;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Chest GUI for configuring the main lobby and every registered game's waiting lobby. */
public class LobbySettingMenu extends Menu {
    private static final int PAGE_SIZE = 45;
    private final int page;

    public LobbySettingMenu(Player player, Object... args) {
        super(player, args);
        int requestedPage = args.length > 0 && args[0] instanceof Integer value ? value : 0;
        this.page = Math.max(0, requestedPage);
    }

    @Override
    protected void setup() {
        List<String> lobbyIds = new ArrayList<>(MCZJUGameCore.getGameManager().getRegisteredGameIds());
        lobbyIds.sort(Comparator.naturalOrder());
        lobbyIds.addFirst(LobbyManager.MAIN_LOBBY_ID);

        int maxPage = Math.max(0, (lobbyIds.size() - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, maxPage);
        int from = actualPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, lobbyIds.size());
        for (int i = from; i < to; i++) {
            String lobbyId = lobbyIds.get(i);
            setSlot(i - from, createLobbyItem(lobbyId), (clickedPlayer, event) -> {
                if (event.getClick() == ClickType.RIGHT) {
                    if (MCZJUGameCore.getLobbyManager().removeLobby(lobbyId)) {
                        clickedPlayer.sender().success("已移除 %s 的大厅位置".formatted(displayName(lobbyId)));
                        refresh();
                    }
                    return;
                }

                clickedPlayer.player().closeInventory();
                clickedPlayer.selectLocation(location -> {
                    MCZJUGameCore.getLobbyManager().setLobby(lobbyId, location);
                    clickedPlayer.sender().success("已设置 %s 的大厅位置".formatted(displayName(lobbyId)));
                    new LobbySettingMenu(clickedPlayer.player(), actualPage).open();
                });
            });
        }

        if (actualPage > 0) setSlot(45, ItemBuilder.of(Material.ARROW).customName("<yellow>上一页").build(),
                (p, event) -> new LobbySettingMenu(p.player(), actualPage - 1).open());
        setSlot(49, ItemBuilder.of(Material.COMPASS)
                .customName("<gold>大厅位置配置")
                .lore(List.of("<gray>左键：设置位置", "<gray>右键：移除位置",
                        "<yellow>第 %d/%d 页".formatted(actualPage + 1, maxPage + 1))).build());
        if (actualPage < maxPage) setSlot(53, ItemBuilder.of(Material.ARROW).customName("<yellow>下一页").build(),
                (p, event) -> new LobbySettingMenu(p.player(), actualPage + 1).open());
    }

    private org.bukkit.inventory.ItemStack createLobbyItem(String lobbyId) {
        Location location = MCZJUGameCore.getLobbyManager().getLobby(lobbyId);
        Material icon = LobbyManager.MAIN_LOBBY_ID.equals(lobbyId)
                ? Material.NETHER_STAR
                : MCZJUGameCore.getGameManager().getGameMetas().get(lobbyId).icon();
        List<String> lore = new ArrayList<>();
        if (location == null) {
            lore.add("<red>未配置");
        } else if (location.getWorld() == null) {
            lore.add("<red>配置世界未加载");
        } else {
            DecimalFormat df = new DecimalFormat("#.##");
            lore.add("<green>已配置");
            lore.add("<gray>世界：<white>" + location.getWorld().getName());
            lore.add("<gray>坐标：<white>%s, %s, %s".formatted(
                    df.format(location.getX()), df.format(location.getY()), df.format(location.getZ())));
        }
        lore.add("");
        lore.add("<yellow>左键设置当前位置");
        lore.add("<yellow>右键移除配置");
        return ItemBuilder.of(icon).customName(displayName(lobbyId)).lore(lore).glint(location != null).build();
    }

    private String displayName(String lobbyId) {
        if (LobbyManager.MAIN_LOBBY_ID.equals(lobbyId)) return "<gold>主大厅";
        GameMeta meta = MCZJUGameCore.getGameManager().getGameMetas().get(lobbyId);
        return meta == null ? "<yellow>" + lobbyId : meta.displayName() + " <gray>(" + lobbyId + ")";
    }

    @Override protected String getTitle() { return "大厅位置配置"; }
    @Override protected @Range(from = 1, to = 6) int getRows() { return 6; }
    @Override protected String getPermission() { return "mgc.dev"; }
}
