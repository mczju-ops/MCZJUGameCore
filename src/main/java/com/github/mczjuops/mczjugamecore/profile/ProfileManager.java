package com.github.mczjuops.mczjugamecore.profile;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.item.LobbyMenuClock;
import com.github.mczjuops.mczjugamecore.item.MGCMaterial;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileManager implements Listener {

    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));
    private final Map<UUID, ProfileData> playerProfileCache = new ConcurrentHashMap<>();
    private BukkitTask autoSaveTask;

    public void switchProfile(Player player, String targetProfileId) {

        ProfileData data = playerProfileCache.get(player.getUniqueId()); // 内存缓存
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // 主线程同步：捕获 + 应用
        String fromProfileId = data.currentProfileId();

        // 捕获当前状态并存入旧 profile
        PlayerSnapshot current = MCZJUGameCore.getProfileCapture().capture(player);
        data.putProfile(fromProfileId, current);

        // 取出目标 profile（null 表示首次进入，apply 会全清）
        PlayerSnapshot target = data.getProfile(targetProfileId);

        // 应用目标（含 closeInventory）
        MCZJUGameCore.getProfileCapture().apply(player, target);

        applyConfiguredProfileState(player, targetProfileId);

        // 更新当前 profile 记录
        data.setCurrentProfileId(targetProfileId);
        data.setPlayerName(playerName); // 同步最新名称

        // 异步落盘快照
        Map<String, PlayerSnapshot> profilesSnapshot = data.snapshotForSave();
        String currentId = data.currentProfileId();
        long ts = data.lastModified();

        if (!MCZJUGameCore.getInstance().isEnabled()) {
            try {
                MCZJUGameCore.getProfileStorageManager().save(
                        uuid, playerName,
                        currentId, profilesSnapshot, ts
                );
            } catch (IOException e) {
                logger.error("玩家 %s 的 profile 数据落盘失败：%s".formatted(playerName, e));
            }
        } else {
            Bukkit.getAsyncScheduler().runNow(MCZJUGameCore.getInstance(), task -> {
                try {
                    MCZJUGameCore.getProfileStorageManager().save(
                            uuid, playerName,
                            currentId, profilesSnapshot, ts
                    );
                } catch (IOException e) {
                    logger.error("玩家 %s 的 profile 数据落盘失败：%s".formatted(playerName, e));
                }
            });
        }

        logger.info("将玩家 %s 的 profile 切换为 %s".formatted(playerName, currentId));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        MCZJUGameCore plugin = MCZJUGameCore.getInstance();

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                Optional<ProfileData> loaded = MCZJUGameCore.getProfileStorageManager().load(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    // 存在该玩家的数据，设置 profile
                    if (loaded.isPresent()) {
                        ProfileData data = loaded.get();

                        playerProfileCache.put(uuid, data);
                        MCZJUGameCore.getProfileCapture().apply(
                                player,
                                data.getProfile(data.currentProfileId())
                        );
                        applyConfiguredProfileState(player, data.currentProfileId());
                        return;
                    }

                    // 没有旧数据，可能是老玩家在首次安装插件后进入服务器，不要 apply 空的 profile，否则就清空原来的物品栏等数据了
                    ProfileData data = new ProfileData(
                            uuid,
                            player.getName(),
                            ProfileData.LOBBY_PROFILE_ID
                    );

                    // 把玩家当前物品栏捕获成初始 profile
                    PlayerSnapshot current =
                            MCZJUGameCore.getProfileCapture().capture(player);

                    data.putProfile(ProfileData.LOBBY_PROFILE_ID, current);
                    data.setCurrentProfileId(ProfileData.LOBBY_PROFILE_ID);
                    data.setPlayerName(player.getName());

                    playerProfileCache.put(uuid, data);
                    applyConfiguredProfileState(player, ProfileData.LOBBY_PROFILE_ID);

                    Map<String, PlayerSnapshot> profilesSnapshot = data.snapshotForSave();
                    String currentId = data.currentProfileId();
                    String nameForSave = data.playerName();
                    long ts = data.lastModified();

                    Bukkit.getAsyncScheduler().runNow(plugin, saveTask -> {
                        try {
                            MCZJUGameCore.getProfileStorageManager().save(
                                    uuid,
                                    nameForSave,
                                    currentId,
                                    profilesSnapshot,
                                    ts
                            );
                        } catch (IOException e) {
                            logger.error(
                                    "玩家 %s 的初始 profile 数据落盘失败：%s"
                                            .formatted(nameForSave, e)
                            );
                        }
                    });
                });

            } catch (IOException e) {
                logger.error("玩家 %s 的 profile 加载失败：%s".formatted(player.getName(), e));
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(TextParser.parse("<red>加载物品栏失败，请询问管理员")));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ProfileData data = playerProfileCache.remove(uuid);
        if (data == null) return;

        PlayerSnapshot last = MCZJUGameCore.getProfileCapture().capture(player);
        data.putProfile(data.currentProfileId(), last);
        data.setPlayerName(player.getName());

        // 快照（主线程）
        Map<String, PlayerSnapshot> profilesSnapshot = data.snapshotForSave();
        String currentId = data.currentProfileId();
        String playerName = data.playerName();
        long ts = data.lastModified();

        // 异步落盘 → 落盘完成后 evict 锁缓存
        Bukkit.getAsyncScheduler().runNow(MCZJUGameCore.getInstance(), task -> {
            try {
                MCZJUGameCore.getProfileStorageManager().save(uuid, playerName, currentId, profilesSnapshot, ts);
            } catch (IOException e) {
                logger.error("玩家 %s 离线时 profile 数据落盘失败：%s".formatted(playerName, e));
            } finally {
                MCZJUGameCore.getProfileStorageManager().evict(uuid);
            }
        });
    }

    public void startAutoSave(long intervalTicks) {
        MCZJUGameCore plugin = MCZJUGameCore.getInstance();

        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        autoSaveTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::autoSaveOnlineProfiles,
                intervalTicks,
                intervalTicks
        );
    }

    private void autoSaveOnlineProfiles() {
        MCZJUGameCore plugin = MCZJUGameCore.getInstance();

        // 当前方法在主线程执行，可以安全 capture Player
        List<ProfileSaveJob> jobs = new ArrayList<>();

        for (Map.Entry<UUID, ProfileData> entry : playerProfileCache.entrySet()) {
            UUID uuid = entry.getKey();
            ProfileData data = entry.getValue();

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            // 关键：把玩家当前状态捕获到当前 profile
            PlayerSnapshot current =
                    MCZJUGameCore.getProfileCapture().capture(player);

            data.putProfile(data.currentProfileId(), current);
            data.setPlayerName(player.getName());

            // 在主线程生成保存快照，异步线程只负责写文件
            jobs.add(new ProfileSaveJob(
                    uuid,
                    data.playerName(),
                    data.currentProfileId(),
                    data.snapshotForSave(),
                    data.lastModified()
            ));
        }

        if (jobs.isEmpty()) return;

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            for (ProfileSaveJob job : jobs) {
                try {
                    MCZJUGameCore.getProfileStorageManager().save(
                            job.uuid(),
                            job.playerName(),
                            job.currentProfileId(),
                            job.profilesSnapshot(),
                            job.lastModified()
                    );
                } catch (IOException e) {
                    logger.error("玩家 %s 的 profile 自动保存失败：%s".formatted(job.playerName(), e));
                }
            }
        });
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
    }

    /**
     * 用于给特定的profile在切换时，加上一些效果，比如在lobby状态时给饱和、给钟。
     * 感觉只有lobby profile会用到，先这样写，后面如果要扩展时，则删了这个函数
     * @param player 玩家
     * @param profileId profile id
     */
    private void applyConfiguredProfileState(Player player, String profileId) {
        var registeredItem = MCZJUGameCore.getItemManager().get(MGCMaterial.LOBBY_MENU_CLOCK.toString());
        LobbyMenuClock lobbyMenuClock = registeredItem instanceof LobbyMenuClock clock ? clock : null;

        if (lobbyMenuClock != null) {
            if (MCZJUGameCore.getConfigManager().isLobbyProfileFeaturesEnabled()
                    && ProfileData.LOBBY_PROFILE_ID.equals(profileId)) {
                lobbyMenuClock.ensureInFixedSlot(player);
            } else {
                lobbyMenuClock.ensureInFixedSlotIfPresent(player);
            }
        }

        if (!MCZJUGameCore.getConfigManager().isLobbyProfileFeaturesEnabled()) {
            return;
        }

        if (!ProfileData.LOBBY_PROFILE_ID.equals(profileId)) {
            player.removePotionEffect(PotionEffectType.SATURATION);
            return;
        }

        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setGameMode(GameMode.ADVENTURE);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SATURATION,
                PotionEffect.INFINITE_DURATION,
                0,
                false,
                false,
                true
        ));
    }

    // onDisable() 调用
    public void shutdown() {

        stopAutoSave();

        for (Map.Entry<UUID, ProfileData> entry : playerProfileCache.entrySet()) {
            UUID uuid = entry.getKey();
            Player p = Bukkit.getPlayer(uuid);
            ProfileData data = entry.getValue();

            if (p != null) {
                PlayerSnapshot last = MCZJUGameCore.getProfileCapture().capture(p);
                data.putProfile(data.currentProfileId(), last);
            }

            try {
                // 关服时同步写
                MCZJUGameCore.getProfileStorageManager().save(uuid, data.playerName(), data.currentProfileId(),
                        data.snapshotForSave(), data.lastModified());
            } catch (IOException e) {
                logger.error("关服落盘玩家 %s profile 数据失败：%s".formatted(data.playerName(), e));
            }
        }
        playerProfileCache.clear();
    }

    private record ProfileSaveJob(
            UUID uuid,
            String playerName,
            String currentProfileId,
            Map<String, PlayerSnapshot> profilesSnapshot,
            long lastModified
    ) {}
}
