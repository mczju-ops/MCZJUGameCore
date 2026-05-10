package com.github.mczjuops.mczjugamecore.profile;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <h3>文件布局（dataDir 下）</h3>
 * <pre>
 *   {uuid}.yml — 主档案
 *   {uuid}.yml.tmp — 写入中的临时文件
 *   {uuid}.bak.yml — 备份，上次保存前的主文件
 * </pre>
 *
 * <h3>写入流程</h3>
 * <ol>
 *   <li>序列化数据写入 {@code {uuid}.yml.tmp}</li>
 *   <li>如果主文件存在，将主文件复制为 {@code {uuid}.bak.yml}</li>
 *   <li>使用临时文件替换主文件</li>
 * </ol>
 *
 * <h3>并发控制</h3>
 * 每个 UUID 持有独立 {@link ReentrantLock}，同一玩家的读写严格串行
 */
public final class ProfileStorageManager {

    private final Path dataDir;
    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));

    /** 独立锁，确保同一玩家的文件操作严格串行 */
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ProfileStorageManager(){
        this.dataDir = MCZJUGameCore.getInstance().getDataFolder().toPath().resolve("profiles");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            logger.error("无法创建 profiles 文件夹：" + e);
        }
    }

    public Optional<ProfileData> load(UUID uuid) throws IOException {
        ReentrantLock lock = getLock(uuid);
        lock.lock();
        try {
            return loadLocked(uuid);
        } finally {
            lock.unlock();
        }
    }

    private Optional<ProfileData> loadLocked(UUID uuid) throws IOException {
        List<Path> candidates = buildCandidates(uuid);

        for (int i = 0; i < candidates.size(); i++) {
            Path file = candidates.get(i);
            if (!Files.exists(file)) continue;

            try {
                ProfileData data = parseFile(file, uuid);

                if (i > 0) {
                    logger.warn("主档案损坏，已从备份恢复：" + uuid);
                }

                return Optional.of(data);
            } catch (Exception e) {
                logger.warn("无法解析%s：%s".formatted(file.getFileName(), e.getMessage()));
            }
        }

        // 无任何候选文件 → 首次上线
        boolean anyExists = candidates.stream().anyMatch(Files::exists);
        if (!anyExists) return Optional.empty();

        // 文件存在但全部损坏
        throw new IOException("玩家 %s 的主档案和备份均损坏，请检查目录 %s".formatted(uuid, dataDir));
    }

    public void save(
            UUID uuid, String playerName, String currentProfileId,
            Map<String, PlayerSnapshot> profiles, long lastModified
    ) throws IOException {

        Path target = mainFile(uuid);
        Path tmp = dataDir.resolve(uuid + ".yml.tmp");

        ReentrantLock lock = getLock(uuid);
        lock.lock();
        try {
            // 1. 序列化到临时文件
            try {
                YamlConfiguration yaml = serialize(
                        uuid, playerName, currentProfileId, profiles, lastModified);
                yaml.save(tmp.toFile());
            } catch (IOException e) {
                throw new IOException("写入临时文件失败：" + tmp, e);
            }

            // 2. 只备份一次：保存前将当前主文件复制成唯一备份
            backupCurrentMain(uuid, target);

            // 3. 替换主文件
            try {
                Files.move(tmp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    logger.warn("不支持 ATOMIC_MOVE，已降级为普通替换（" + uuid + "）");
                } catch (IOException e2) {
                    throw new IOException("替换主文件失败：" + target, e2);
                }
            } catch (IOException e) {
                throw new IOException("原子替换失败：" + target, e);
            }

        } finally {
            lock.unlock();
        }
    }

    public void delete(UUID uuid) throws IOException {
        ReentrantLock lock = getLock(uuid);
        lock.lock();
        try {
            List<Path> targets = new ArrayList<>(buildCandidates(uuid));
            targets.add(dataDir.resolve(uuid + ".yml.tmp"));

            for (Path f : targets) {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException e) {
                    throw new IOException("删除文件失败：" + f, e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 玩家下线后由外部管理器调用，清理锁缓存以防内存泄漏。
     * 仅在锁空闲时移除。
     */
    public void evict(UUID uuid) {
        ReentrantLock lock = locks.get(uuid);
        if (lock != null && !lock.isLocked()) {
            locks.remove(uuid, lock);
        }
    }

    private YamlConfiguration serialize(
            UUID uuid, String playerName, String currentProfileId,
            Map<String, PlayerSnapshot> profiles, long lastModified
    ) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("formatVersion", ProfileData.FORMAT_VERSION);
        yaml.set("uuid", uuid.toString());
        yaml.set("playerName", playerName);
        yaml.set("currentProfileId", currentProfileId);
        yaml.set("lastModified", lastModified);

        ConfigurationSection profilesSection = yaml.createSection("profiles");
        for (Map.Entry<String, PlayerSnapshot> entry : profiles.entrySet()) {
            ConfigurationSection sec = profilesSection.createSection(entry.getKey());
            writeSnapshot(sec, entry.getValue());
        }

        return yaml;
    }

    private void writeSnapshot(ConfigurationSection sec, PlayerSnapshot snap) {
        sec.set("main", encodeBytes(snap.mainContents()));
        sec.set("armor", encodeBytes(snap.armorContents()));
        sec.set("offhand", encodeBytes(snap.offhand()));
        sec.set("enderChest", encodeBytes(snap.enderChest()));
        sec.set("xpLevel", snap.xpLevel());
        sec.set("xpProgress", (double) snap.xpProgress());
    }

    // 反序列化
    private ProfileData parseFile(Path file, UUID uuid) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());

        int version = yaml.getInt("formatVersion", 1);
        if (version != ProfileData.FORMAT_VERSION) {
            logger.warn("版本不匹配（期望%s，实际%s），尝试兼容读取 %s".formatted(ProfileData.FORMAT_VERSION, version, uuid));
        }

        String playerName = yaml.getString("playerName", "Unknown");
        String currentProfile = yaml.getString(
                "currentProfileId",
                ProfileData.LOBBY_PROFILE_ID
        );
        long lastModified = yaml.getLong("lastModified", System.currentTimeMillis());

        Map<String, PlayerSnapshot> profiles = new LinkedHashMap<>();
        ConfigurationSection profilesSection = yaml.getConfigurationSection("profiles");

        if (profilesSection != null) {
            for (String profileId : profilesSection.getKeys(false)) {
                ConfigurationSection sec = profilesSection.getConfigurationSection(profileId);
                if (sec == null) continue;

                profiles.put(profileId, readSnapshot(sec, uuid, profileId));
            }
        }

        return new ProfileData(uuid, playerName, currentProfile, profiles, lastModified);
    }

    private PlayerSnapshot readSnapshot(ConfigurationSection sec, UUID uuid, String profileId) {
        try {
            return new PlayerSnapshot(
                    decodeBytes(sec.getString("main", "")),
                    decodeBytes(sec.getString("armor", "")),
                    decodeBytes(sec.getString("offhand", "")),
                    decodeBytes(sec.getString("enderChest", "")),
                    sec.getInt("xpLevel", 0),
                    (float) sec.getDouble("xpProgress", 0.0)
            );
        } catch (Exception e) {
            logger.warn("Profile %s 反序列化失败（%s），已降级为空快照：%s".formatted(profileId, uuid, e.getMessage()));
            return PlayerSnapshot.empty();
        }
    }

    /** 保存前备份当前主文件。如果备份失败，不阻止主文件写入，只记录警告 */
    private void backupCurrentMain(UUID uuid, Path mainTarget) {
        if (!Files.exists(mainTarget)) return;

        Path backup = backupFile(uuid);

        try {
            Files.copy(mainTarget, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warn("备份主 profile 失败，继续保存主文件（%s）：%s".formatted(uuid, e.getMessage()));
        }
    }

    private static String encodeBytes(byte[] data) {
        if (data == null || data.length == 0) return "";
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Base64 → byte[]。
     * 解码失败时返回空 byte[]。
     */
    private byte[] decodeBytes(String base64) {
        if (base64 == null || base64.isBlank()) return new byte[0];

        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            logger.warn("Base64 解码失败，返回空数据：" + e.getMessage());
            return new byte[0];
        }
    }

    private Path mainFile(UUID uuid) {
        return dataDir.resolve(uuid + ".yml");
    }

    private Path backupFile(UUID uuid) {
        return dataDir.resolve(uuid + ".bak.yml");
    }

    /** 返回 [主文件, 备份文件] 顺序的候选列表，用于容错加载 */
    private List<Path> buildCandidates(UUID uuid) {
        List<Path> list = new ArrayList<>(2);
        list.add(mainFile(uuid));
        list.add(backupFile(uuid));
        return list;
    }

    private ReentrantLock getLock(UUID uuid) {
        return locks.computeIfAbsent(uuid, k -> new ReentrantLock());
    }
}