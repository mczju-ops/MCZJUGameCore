package com.github.mczjuops.mczjugamecore;

import com.github.mczjuops.mczjugamecore.command.BrigadierCommand;
import com.github.mczjuops.mczjugamecore.command.MGCCommand;
import com.github.mczjuops.mczjugamecore.command.MGCOPCommand;
import com.github.mczjuops.mczjugamecore.command.MenuCommand;
import com.github.mczjuops.mczjugamecore.command.partycommand.PartyCommand;
import com.github.mczjuops.mczjugamecore.game.manager.AbstractGameManager;
import com.github.mczjuops.mczjugamecore.game.manager.DefaultGameManager;
import com.github.mczjuops.mczjugamecore.game.room.GameRoomManager;
import com.github.mczjuops.mczjugamecore.initialize.ItemInitializer;
import com.github.mczjuops.mczjugamecore.initialize.ListenerInitializer;
import com.github.mczjuops.mczjugamecore.initialize.MenuInitializer;
import com.github.mczjuops.mczjugamecore.item.ItemManager;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.player.AbstractPlayerManager;
import com.github.mczjuops.mczjugamecore.player.DefaultPlayerManager;
import com.github.mczjuops.mczjugamecore.player.party.PartyManager;
import com.github.mczjuops.mczjugamecore.profile.ProfileManager;
import com.github.mczjuops.mczjugamecore.profile.ProfileCapture;
import com.github.mczjuops.mczjugamecore.profile.ProfileStorageManager;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class MCZJUGameCore extends JavaPlugin {

    private static MCZJUGameCore INSTANCE;
    private AbstractGameManager gameManager;

    private AbstractPlayerManager playerManager;

    private PartyManager partyManager;
    private GameRoomManager gameRoomManager;

    private ItemManager itemManager;
    private MenuFacade menuFacade;

    private ProfileManager profileManager;
    private ProfileCapture profileCapture;
    private ProfileStorageManager profileStorageManager;

    @Override
    public void onEnable() {
        // Plugin startup logic

        saveDefaultConfig();
        INSTANCE = this;
        gameManager = new DefaultGameManager();
        partyManager = new PartyManager();
        playerManager = new DefaultPlayerManager();
        gameRoomManager = new GameRoomManager();
        menuFacade = new MenuFacade();
        itemManager = new ItemManager();

        profileManager = new ProfileManager();
        profileCapture = new ProfileCapture();
        profileStorageManager = new ProfileStorageManager();

        MenuInitializer.initialize();
        ListenerInitializer.initialize();
        ItemInitializer.initialize();

        registerCommands(
                new MGCCommand(),
                new MGCOPCommand(),
                new PartyCommand(),
                new MenuCommand()
        );
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        gameRoomManager.saveAllGameRoomDirectly();  // 保存所有游戏房间
        profileManager.shutdown(); // 保存所有玩家的 profile 数据
    }

    public static @NotNull MCZJUGameCore getInstance(){
        if (INSTANCE == null){
            throw new RuntimeException("请勿在MCZJUGameCore初始化前，调用getInstance()函数！");
        }
        return INSTANCE;
    }

    /**
     * 获取player manager，用于查看某个游戏有哪些玩家加入、玩家状态之类的
     * @return player manager
     */
    public static @NotNull AbstractPlayerManager getPlayerManager(){
        return getInstance().playerManager;
    }

    /**
     * 获取game manager，用于注册游戏等
     * @return game manager
     */
    public static @NotNull AbstractGameManager  getGameManager(){
        return getInstance().gameManager;
    }
    public static @NotNull ConsoleSender getConsoleSender(){
        return new ConsoleSender("MGC");
    }

    public static @NotNull PartyManager getPartymanager(){
        return getInstance().partyManager;
    }

    public static @NotNull GameRoomManager getGameRoomManager(){
        return getInstance().gameRoomManager;
    }

    public static @NotNull MenuFacade getMenuFacade(){
        return getInstance().menuFacade;
    }
    public static @NotNull ItemManager getItemManager(){
        return getInstance().itemManager;
    }

    public static @NotNull FileConfiguration getMGCConfig(){
        return getInstance().getConfig();
    }

    public static @NotNull ProfileManager getProfileManager(){
        return getInstance().profileManager;
    }
    public static @NotNull ProfileCapture getProfileCapture() {
        return getInstance().profileCapture;
    }
    public static @NotNull ProfileStorageManager getProfileStorageManager(){
        return getInstance().profileStorageManager;
    }

    public static boolean isDebug(){
        if (INSTANCE == null) return false;
        return getMGCConfig().getBoolean("debug");
    }

    private void registerCommands(BrigadierCommand... commands) {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            for (BrigadierCommand cmd : commands) {
                try {
                    LiteralCommandNode<CommandSourceStack> root = cmd.getNode();

                    // 主命令 + 顶层 aliases
                    registrar.register(root, cmd.getDescription(), cmd.getAliases());
                    String aliases = cmd.getAliases().isEmpty()
                            ? "none"
                            : String.join(", ", cmd.getAliases());
                    getLogger().info("Brigadier command registered: /%s | aliases: %s".formatted(cmd.getName(), aliases));

                    // 子命令重定向别名（/p list -> /pl）
                    for (BrigadierCommand.RedirectAlias ra : cmd.getRedirectAliases()) {
                        CommandNode<CommandSourceStack> target = resolvePath(root, ra.path());
                        var builder = Commands.literal(ra.name()).requires(target.getRequirement());
                        if (target.getCommand() != null) {
                            builder.executes(target.getCommand()); // 为了处理 /p list 这种后面没有更多参数的情况
                        }
                        LiteralCommandNode<CommandSourceStack> aliasNode = builder.redirect(target).build();
                        registrar.register(aliasNode, ra.description());
                        getLogger().info("Brigadier redirect alias registered: /%s -> /%s ".formatted(ra.name(), cmd.getName())
                                + String.join(" ", ra.path()));
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to register: /%s - %s".formatted(cmd.getName(), e.getMessage()));
                }
            }
        });
    }

    private static CommandNode<CommandSourceStack> resolvePath(
            CommandNode<CommandSourceStack> root, List<String> path
    ) {
        CommandNode<CommandSourceStack> current = root;
        for (String segment : path) {
            CommandNode<CommandSourceStack> next = current.getChild(segment);
            if (next == null) {
                throw new IllegalArgumentException(
                        "Redirect path segment not found: '%s' under '%s'".formatted(segment, current.getName()));
            }
            current = next;
        }
        return current;
    }
}
