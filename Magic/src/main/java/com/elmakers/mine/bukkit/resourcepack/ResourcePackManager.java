package com.elmakers.mine.bukkit.resourcepack;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.elmakers.mine.bukkit.api.rp.ResourcePackStatus;
import com.elmakers.mine.bukkit.magic.Mage;
import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.tasks.RPCheckTask;
import com.elmakers.mine.bukkit.utility.CompatibilityLib;
import com.elmakers.mine.bukkit.utility.TextUtils;
import com.google.common.io.BaseEncoding;

public class ResourcePackManager {
    private static final String RP_FILE = "resourcepack";

    private final MagicController controller;
    private boolean enableResourcePackCheck = true;
    private boolean resourcePackFromServer = false;
    private boolean resourcePacksEnabled = true;
    private int resourcePackPromptDelay = 0;
    private boolean resourcePackPrompt = false;
    private int resourcePackCheckInterval = 0;
    private int resourcePackCheckTimer = 0;
    private String defaultResourcePack = null;
    private String resourcePack = null;
    private byte[] resourcePackHash = null;
    private long resourcePackDelay = 0;
    private boolean isResourcePackEnabledByDefault = true;
    private final Map<String, ResourcePack> resourcePacks = new HashMap<>();
    private boolean resourcePackConfigurationLoaded = false;
    private ConfigurationSection alternateResourcePacks = null;

    public ResourcePackManager(MagicController controller) {
        this.controller = controller;
    }

    public void load(ConfigurationSection properties, CommandSender sender, boolean firstLoad) {
        String currentResourcePack = defaultResourcePack;
        isResourcePackEnabledByDefault = properties.getBoolean("resource_pack_default_auto", true);

        resourcePackPrompt = properties.getBoolean("resource_pack_prompt", false);
        enableResourcePackCheck = properties.getBoolean("enable_resource_pack_check", true);
        resourcePackCheckInterval = properties.getInt("resource_pack_check_interval", 0);
        resourcePackPromptDelay = properties.getInt("resource_pack_prompt_delay", 0);
        defaultResourcePack = properties.getString("resource_pack", null);
        // For legacy configs
        defaultResourcePack = properties.getString("default_resource_pack", defaultResourcePack);
        // For combined configs
        if (controller.hasAddedExamples() && !defaultResourcePack.isEmpty() && !defaultResourcePack.startsWith("http")) {
            defaultResourcePack = properties.getString("add_resource_pack", defaultResourcePack);
        }

        List<String> resourcePackPlugins = controller.getResourcePackPlugins();
        boolean resourcePacksEnableConfiguration = properties.getBoolean("enable_resource_pack");
        if (!resourcePacksEnableConfiguration || !resourcePackPlugins.isEmpty()) {
            defaultResourcePack = null;
        }

        // For reloading after disabling the RP
        if (defaultResourcePack == null || defaultResourcePack.isEmpty()) {
            resourcePack = null;
            resourcePackHash = null;
        }

        resourcePackDelay = properties.getLong("resource_pack_delay", 0);
        alternateResourcePacks = properties.getConfigurationSection("alternate_resource_packs");

        if (defaultResourcePack != null) {
            ConfigurationSection altDefault = alternateResourcePacks.getConfigurationSection(defaultResourcePack);
            if (altDefault != null) {
                defaultResourcePack = altDefault.getString("url");
            }
        }

        if (!firstLoad && !Objects.equals(defaultResourcePack, currentResourcePack)) {
            checkResourcePack(sender, false, false, true);
        }
    }

    public boolean sendResourcePackToAllPlayers(CommandSender sender) {
        if (resourcePack == null || resourcePackHash == null) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "No RP set or RP already set in server.properties, not sending.");
            }
            return false;
        }
        int sent = 0;
        int skipped = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Mage mage = controller.getRegisteredMage(player);
            if (mage != null && !mage.isResourcePackEnabled()) {
                skipped++;
                continue;
            } else if (mage != null) {
                sendResourcePack(mage);
            }
            sent++;
        }
        if (sender != null) {
            sender.sendMessage(ChatColor.AQUA + "Sent current RP to " + sent + " players, skipped " + skipped + " players");
        }

        return true;
    }

    protected boolean promptResourcePack(final Player player, String message) {
        if (message != null && !message.isEmpty()) {
            if (resourcePackPromptDelay <= 0) {
                TextUtils.sendMessage(player, controller.getMessagePrefix(), message);
            } else {
                Plugin plugin = controller.getPlugin();
                plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override
                    public void run() {
                        TextUtils.sendMessage(player, controller.getMessagePrefix(), message);
                    }
                }, resourcePackPromptDelay / 50);
            }
        }
        return true;
    }

    public boolean checkPromptResourcePack(final Player player) {
        if (resourcePack == null || resourcePackHash == null) {
            return false;
        }

        if (resourcePackPrompt) {
            String message = controller.getMessages().get("resource_pack.prompt");
            promptResourcePack(player, message);
            return false;
        }

        return sendResourcePack(player);
    }

    public boolean checkPromptResourcePack(final Player player, String resourcePackKey) {
        ResourcePack resourcePack = null;
        if (resourcePackKey != null && !resourcePackKey.isEmpty()) {
            // First check for a predefined alternate RP
            String url = resourcePackKey;
            ConfigurationSection alternateConfig = alternateResourcePacks.getConfigurationSection(resourcePackKey);
            if (alternateConfig != null) {
                url = alternateConfig.getString("url");
            }
            resourcePack = createResourcePack(url);
        }
        if (resourcePackPrompt) {
            String message = controller.getMessages().get("resource_pack.prompt");
            promptResourcePack(player, message);
            return false;
        }

        return checkAndSendResourcePack(controller.getMage(player), resourcePack);
    }

    public boolean promptNoResourcePack(final Player player) {
        if (resourcePack == null || resourcePackHash == null) {
            return false;
        }

        String message = controller.getMessages().get("resource_pack.off_prompt");
        return promptResourcePack(player, message);
    }

    public boolean checkAndSendResourcePack(final Mage mage, ResourcePack rp) {
        if (rp == null) {
            rp = getPreferredResourcePack(mage);
        }
        if (rp == null) {
            return false;
        }
        if (!rp.isChecked()) {
            final ResourcePack checkRP = rp;
            updateResourcePackHash(rp, false, false, new ResourcePackResponse() {
                @Override
                public void finished(boolean success, boolean hasModifiedTime, List<String> responses, ResourcePack pack) {
                    Plugin plugin = controller.getPlugin();
                    CommandSender sender = Bukkit.getConsoleSender();
                    if (plugin.isEnabled()) {
                        for (String response : responses) {
                            sender.sendMessage(response);
                        }
                    }
                    if (success) {
                        sendResourcePack(mage, checkRP);
                    }
                }
            });
            return true;
        }
        return sendResourcePack(mage, rp);
    }

    public ResourcePack getPreferredResourcePack(Mage mage) {
        String url = getResourcePackUrl(mage.getPreferredResourcePack());
        if (url == null) {
            url = resourcePack;
        }
        if (url == null || url.isEmpty()) {
            return null;
        }
        return createResourcePack(url);
    }

    public boolean sendResourcePack(final Mage mage) {
        ResourcePack rp = getPreferredResourcePack(mage);
        if (rp == null) {
            return false;
        }
        return checkAndSendResourcePack(mage, rp);
    }

    public boolean sendResourcePack(final Player player) {
        return sendResourcePack(controller.getMage(player));
    }

    private boolean sendResourcePack(final Mage mage, ResourcePack pack) {
        return sendResourcePack(mage, pack.getUrl(), pack.getHash());
    }

    public boolean sendResourcePack(final Mage mage, String url, byte[] hash) {
        if (url == null || hash == null) {
            return false;
        }
        Player player = mage.getPlayer();
        String message = controller.getMessages().get("resource_pack.sending");
        if (!message.isEmpty()) {
            TextUtils.sendMessage(player, controller.getMessagePrefix(), message);
        }

        // Give them some time to read the message
        Bukkit.getScheduler().runTaskLater(controller.getPlugin(), new Runnable() {
            @Override
            public void run() {
                String sendURL = url;
                String hashString;
                try {
                    hashString = BaseEncoding.base64().encode(hash);
                    // MC 1.17 got super dumb about RPs changing their hash without a new URL :(
                    sendURL += "#" + hashString;
                } catch (Exception ex) {
                    hashString = "(Error";
                }
                controller.info("Sending resource pack " + sendURL + " to " + player.getName() + " with hash " + hashString, 10);
                CompatibilityLib.getCompatibilityUtils().setResourcePack(player, sendURL, hash);
            }
        }, resourcePackDelay * 20 / 1000);

        return true;
    }

    protected Map<String, ResourcePack> getResourcePacks() {
        synchronized (resourcePacks) {
            if (!resourcePackConfigurationLoaded) {
                resourcePackConfigurationLoaded = true;
                final File rpFile = new File(controller.getPlugin().getDataFolder(), "data/" + RP_FILE + ".yml");
                if (rpFile.exists()) {
                    try {
                        YamlConfiguration resourcePackConfiguration = new YamlConfiguration();
                        resourcePackConfiguration.load(rpFile);
                        Set<String> keys = resourcePackConfiguration.getKeys(false);
                        for (String key : keys) {
                            resourcePacks.put(key, new ResourcePack(key, resourcePackConfiguration.getConfigurationSection(key)));
                        }
                    } catch (Exception ex) {
                        controller.getLogger().log(Level.WARNING, "Error loading resource pack save file", ex);
                    }
                }
            }
        }
        return resourcePacks;
    }

    protected ResourcePack getResourcePack(String url) {
        return getResourcePacks().get(ResourcePack.getKey(url));
    }

    protected ResourcePack createResourcePack(String url) {
        ResourcePack pack = getResourcePack(url);
        if (pack == null) {
            synchronized (resourcePacks) {
                pack = new ResourcePack(url);
                resourcePacks.put(pack.getKey(), pack);
            }
        }
        pack.setUrl(url);
        return pack;
    }

    public void saveResourcePacks() {
        Map<String, ResourcePack> packs = getResourcePacks();
        synchronized (resourcePacks) {
            try {
                final File rpFile = new File(controller.getPlugin().getDataFolder(), "data/" + RP_FILE + ".yml");
                YamlConfiguration resourcePackConfiguration = new YamlConfiguration();
                for (Map.Entry<String, ResourcePack> entry : packs.entrySet()) {
                    ConfigurationSection save = resourcePackConfiguration.createSection(entry.getKey());
                    entry.getValue().save(save);
                }
                resourcePackConfiguration.save(rpFile);
            } catch (Exception ex) {
                controller.getLogger().log(Level.WARNING, "Error saving resource pack save file", ex);
            }
        }
    }

    public boolean isResourcePackEnabled() {
        return resourcePacksEnabled;
    }

    public boolean isResourcePackFromServer() {
        return resourcePackFromServer;
    }

    public boolean checkResourcePack(final CommandSender sender, final boolean skipMessages, final boolean force, final boolean filenameChanged) {
        final Plugin plugin = controller.getPlugin();
        if (!plugin.isEnabled()) return false;
        final Server server = plugin.getServer();
        resourcePack = null;
        resourcePackHash = null;
        resourcePackFromServer = false;
        resourcePacksEnabled = true;
        final boolean quiet = sender == null || skipMessages;

        if (defaultResourcePack == null || defaultResourcePack.isEmpty()) {
            if (!quiet) sender.sendMessage("Resource pack in config.yml has been disabled, Magic skipping RP check");
            resourcePacksEnabled = false;
            return false;
        }

        String serverResourcePack = CompatibilityLib.getCompatibilityUtils().getResourcePack(server);
        if (serverResourcePack != null) serverResourcePack = serverResourcePack.trim();

        if (serverResourcePack != null && !serverResourcePack.isEmpty()) {
            if (!quiet) sender.sendMessage("Resource pack configured in server.properties, Magic not using RP from config.yml");
            resourcePacksEnabled = false;
            resourcePackFromServer = true;
            return false;
        }
        resourcePack = defaultResourcePack;
        if (!quiet) sender.sendMessage("Magic checking resource pack for updates: " + ChatColor.GRAY + resourcePack);
        ResourcePack resourcePackInfo = createResourcePack(resourcePack);
        updateResourcePackHash(resourcePackInfo, force, filenameChanged, new ResourcePackResponse() {
            @Override
            public void finished(boolean success, boolean hasModifiedTime, List<String> responses, ResourcePack pack) {
                if (!quiet && plugin.isEnabled()) {
                    for (String response : responses) {
                        sender.sendMessage(response);
                    }
                }
                if (!success && !hasModifiedTime) {
                    cancelResourcePackChecks();
                    if (!quiet) {
                        sender.sendMessage("Cancelling automatic RP checks until next restart");
                    }
                } else {
                    resourcePackHash = pack.getHash();
                }
            }
        });
        return true;
    }

    public void updateResourcePackHash(ResourcePack resourcePack, final boolean force, final boolean filenameChanged, ResourcePackResponse callback) {
        final Plugin plugin = controller.getPlugin();
        if (!plugin.isEnabled()) return;
        final Server server = plugin.getServer();
        server.getScheduler().runTaskAsynchronously(plugin, new ResourcePackUpdateRunnable(this, resourcePack, callback, force, filenameChanged));
    }

    protected void cancelResourcePackChecks() {
        if (resourcePackCheckTimer != 0) {
            Bukkit.getScheduler().cancelTask(resourcePackCheckTimer);
            resourcePackCheckTimer = 0;
        }
    }

    public void startResourcePackChecks() {
        if (checkResourcePack(Bukkit.getConsoleSender(), false, false, false) && resourcePackCheckInterval > 0 && enableResourcePackCheck) {
            int intervalTicks = resourcePackCheckInterval * 60 * 20;
            resourcePackCheckTimer = Bukkit.getScheduler().scheduleSyncRepeatingTask(controller.getPlugin(), new RPCheckTask(controller), intervalTicks, intervalTicks);
        }
    }

    public String getDefaultResourcePackURL() {
        return resourcePack;
    }

    public String getResourcePackURL(final CommandSender sender) {
        return getResourcePackURL(controller.getMage(sender));
    }

    public String getResourcePackURL(final Mage mage) {
        String url = getResourcePackUrl(mage.getPreferredResourcePack());
        if (url == null) {
            url = resourcePack;
        }
        return url;
    }

    public boolean isResourcePackEnabledByDefault() {
        return isResourcePackEnabledByDefault;
    }

    public MagicController getController() {
        return controller;
    }

    public void clearChecked() {
        synchronized (resourcePacks) {
            for (ResourcePack pack : resourcePacks.values()) {
                pack.setChecked(false);
            }
        }
    }

    @Nullable
    public Boolean resourcePackUsesSkulls(String packType) {
        ConfigurationSection packConfig = alternateResourcePacks.getConfigurationSection(packType);
        return packConfig == null || !packConfig.contains("url_icons_enabled") ? null : packConfig.getBoolean("url_icons_enabled");
    }

    @Nullable
    public String getResourcePackUrl(String packType) {
        if (packType == null) return null;
        ConfigurationSection packConfig = alternateResourcePacks.getConfigurationSection(packType);
        return packConfig == null ? null : packConfig.getString("url");
    }

    public Collection<String> getAlternateResourcePacks() {
        return alternateResourcePacks.getKeys(false);
    }

    public void onResourcePackStatus(Player player, ResourcePackStatus status) {
        if (!resourcePacksEnabled) return;

        Mage mage = controller.getRegisteredMage(player);
        if (mage == null || mage.isResourcePackDisabled()) return;
        String message;
        switch (status) {
            case UNKNOWN:
                break;
            case LOADED:
                mage.setHasResourcePack(true);
                break;
            case DECLINED:
                mage.setHasResourcePack(false);
                message = controller.getMessages().get("resource_pack.declined");
                mage.sendMessage(message);
                break;
            case FAILED:
                mage.setHasResourcePack(false);
                message = controller.getMessages().get("resource_pack.failed");
                mage.sendMessage(message);
                break;
        }
    }
}
