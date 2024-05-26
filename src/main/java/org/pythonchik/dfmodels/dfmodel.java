package org.pythonchik.dfmodels;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.util.BoundingBox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class dfmodel implements CommandExecutor, Listener, TabCompleter {
    Dfmodels plugin;
    FileConfiguration config;
    HashMap<Player, Long> delay = new HashMap<>();
    private static Message message = Dfmodels.getMessage();
    private final Logger logger = Bukkit.getPluginManager().getPlugin("Dfmodels").getLogger();

    public dfmodel(Dfmodels plugin,FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isplayer = sender instanceof Player;
        if (isplayer) {
            if (args.length == 0) {
                Player player = ((Player) sender);
                if (player.getInventory().getItemInOffHand().getType().equals(Material.WRITTEN_BOOK)) {
                    BookMeta bookmeta = (BookMeta) player.getInventory().getItemInOffHand().getItemMeta();
                    if (bookmeta.hasTitle() && !Pattern.matches("^[a-zA-Zа-яА-ЯёЁ0-9]+$",bookmeta.getTitle())){
                        message.send(player, "Название книги может содержать только буквы Английского и Русского алфавитов и цифры, другие символы не разрешены");
                        return true;
                    }
                    String cmd = "";
                    if (!bookmeta.hasPages()){
                        message.send(player,"В книге должна быть ссылка");
                        return true;
                    }
                    cmd = request(bookmeta.getPage(1));
                    if (cmd == null) {
                        message.send(player, "Ссылка неверна");
                        return true;
                    }

                    if (!isValidCommand(cmd)) {
                        message.send(player, "Команда неверна или модель слишком большая");
                        return true;
                    }

                    HashMap<ItemStack, Integer> materials = parseMaterials(cmd);
                    int count = 0;
                    for (Map.Entry<ItemStack, Integer> entry : materials.entrySet()) {
                        count += entry.getValue();
                    }
                    if (count > 65) {
                        message.send(player, "Команда неверна или модель слишком большая");
                        return true;
                    }
                    HashMap<ItemStack, Integer> missingItems = new HashMap<>();

                    for (Map.Entry<ItemStack, Integer> entry : materials.entrySet()) {
                        ItemStack requiredItem = entry.getKey();
                        int requiredQuantity = entry.getValue();

                        int foundQuantity = 0;

                        for (ItemStack item : player.getInventory().getContents()) {
                            if (item != null && item.isSimilar(requiredItem)) {
                                foundQuantity += item.getAmount();
                            }
                        }

                        if (foundQuantity < requiredQuantity) {
                            missingItems.put(requiredItem, requiredQuantity - foundQuantity);
                        }
                    } //creating item list

                    if (missingItems.isEmpty()) {
                        for (String uuid : config.getKeys(false)){
                            if (bookmeta.getTitle().equals(config.getString(uuid + ".name"))){
                                message.send(sender,"Это имя уже занято, используете другое для избежания конфликтов");
                                return true;
                            }
                        }
                        if (!delay.containsKey(player)) {
                            for (Map.Entry<ItemStack, Integer> entry : materials.entrySet()) {
                                ItemStack item = entry.getKey();
                                int quantity = entry.getValue();
                                player.getInventory().removeItem(new ItemStack(item.getType(), quantity));
                            }
                            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "execute at " + player.getName() + " run " + cmd.replaceFirst("/", "").replace("{Passengers", String.format("{CustomName:'{\"text\":\"%s\"}',Passengers", bookmeta.getTitle())));
                            for (Entity entity : player.getWorld().getNearbyEntities(new BoundingBox(player.getLocation().getX() + 1, player.getLocation().getY() + 1, player.getLocation().getZ() + 1, player.getLocation().getX() - 1, player.getLocation().getY() - 1, player.getLocation().getZ() - 1))) {
                                if (entity.getType() == EntityType.BLOCK_DISPLAY && entity.getVehicle() == null && entity.getCustomName() != null && entity.getCustomName().equals(bookmeta.getTitle())) {
                                    config.set(entity.getUniqueId() + ".name", bookmeta.getTitle());
                                    config.set(entity.getUniqueId() + ".creator", player.getName());
                                    config.set(entity.getUniqueId() + ".x", entity.getLocation().getX());
                                    config.set(entity.getUniqueId() + ".y", entity.getLocation().getY());
                                    config.set(entity.getUniqueId() + ".z", entity.getLocation().getZ());
                                    Dfmodels.saveConfig1(plugin);
                                    if (!player.isOp()) {
                                        delay.put(player, System.currentTimeMillis());
                                        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
                                        service.schedule(() -> {
                                            delay.remove(player);
                                        }, 10 * 60, TimeUnit.SECONDS);
                                        service.shutdown();
                                        break;
                                    }
                                }
                            }
                        } else {
                            message.send(player, "Для использования команды надо подождать еще " + (((delay.get(player) + 10 * 60 * 1000) - System.currentTimeMillis()) / 1000) + "cек.");
                        }
                    } else {
                        message.send(player, "Для установки не хватает еще:");
                        for (Map.Entry<ItemStack, Integer> entry : missingItems.entrySet()) {
                            ItemStack missingItem = entry.getKey();
                            int reqQuantity = entry.getValue();
                            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),String.format("tellraw %s [{\"text\":\"[\",\"color\":\"gray\"},{\"text\":\"DFModel\",\"color\":\"gold\"},{\"text\":\"] \",\"color\":\"gray\"},{\"translate\":\"%s\",\"color\":\"white\"},{\"text\":\" x\"},{\"text\":\"%s\"}]",player.getName(),missingItem.getType().getTranslationKey(),reqQuantity));
                            //message.send(player, missingItem.getType().getTranslationKey() + " x" + reqQuantity);
                        }
                    }
                } else {
                    message.send(player, "Во второй руке надо держать подписанную книгу с ссылкой на первой странице. Прочитать подробнее можно на вики https://wiki.dreamfox.fun/");
                }
            } else if (args[0].equals("pos") && args.length == 5) {
                for (String uuid : config.getKeys(false)) {
                    if (args[1].equals(config.getString(uuid + ".name"))) {
                        for (Entity entity : plugin.getServer().getEntity(UUID.fromString(uuid)).getNearbyEntities(25, 25, 25)) {
                            if (entity instanceof Player && (entity).equals(sender)) {
                                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), String.format("execute as %s at @s run teleport @s %s %s %s", uuid, args[2], args[3], args[4]));
                                if (!plugin.getServer().getEntity(UUID.fromString(uuid)).getNearbyEntities(25,25,25).contains(entity)){
                                    message.send(sender, "Модель не может быть перемещена так далеко");
                                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), String.format("execute as %s at @s run teleport @s %s %s %s", uuid, config.getString(uuid + ".x"), config.getString(uuid + ".y"), config.getString(uuid + ".z")));
                                } else {
                                    config.set(uuid + ".x", plugin.getServer().getEntity(UUID.fromString(uuid)).getLocation().getX());
                                    config.set(uuid + ".y", plugin.getServer().getEntity(UUID.fromString(uuid)).getLocation().getY());
                                    config.set(uuid + ".z", plugin.getServer().getEntity(UUID.fromString(uuid)).getLocation().getZ());
                                    Dfmodels.saveConfig1(plugin);
                                    message.send(sender, "Модель успешно перемещена");
                                }
                                return true;
                            }
                        }
                        message.send(sender, "Вы должны находится рядом с моделью что-бы её переместить");
                        return true;
                    }
                }
            } else if (args[0].equals("spin") && args.length == 4) {
                if (args[2].matches("((-|\\+)?[0-9]+(\\.[0-9]+)?)+") && args[3].matches("((-|\\+)?[0-9]+(\\.[0-9]+)?)+")) {
                    for (String uuid : config.getKeys(false)) {
                        if (args[1].equals(config.getString(uuid + ".name"))) {
                            for (Entity entity : plugin.getServer().getEntity(UUID.fromString(uuid)).getNearbyEntities(25, 25, 25)) {
                                if (entity instanceof Player && ((Player) entity).equals((Player) sender)) {
                                    Entity entity1 = plugin.getServer().getEntity(UUID.fromString(uuid));
                                    entity1.setRotation(Float.parseFloat(args[2]), Float.parseFloat(args[3]));
                                    for (Entity entity2 : entity1.getPassengers()) {
                                        entity2.setRotation(Float.parseFloat(args[2]), Float.parseFloat(args[3]));
                                    }
                                    message.send(sender, "Модель успешно повернута");
                                    return true;
                                }
                            }
                            message.send(sender, "Вы должны находится рядом с моделью что-бы её переместить");
                            return true;
                        }
                    }
                } else {
                    message.send(sender, "Нужно ввести действительные числа");
                    return true;
                }
            } else if (args[0].equals("del")) {
                Player player = ((Player) sender).getPlayer();
                if (!(player.getInventory().getItemInOffHand().hasItemMeta() && player.getInventory().getItemInOffHand().getType().equals(Material.WRITTEN_BOOK))) {
                    message.send(sender,"Во второй руке нужно держать оригинальную книгу модели");
                    return true;
                }
                BookMeta bookMeta = (BookMeta) player.getInventory().getItemInOffHand().getItemMeta();
                if (!player.isOp()) {
                    for (Entity entity : player.getWorld().getNearbyEntities(new BoundingBox(player.getLocation().getX() + 3, player.getLocation().getY() + 3, player.getLocation().getZ() + 3, player.getLocation().getX() - 3, player.getLocation().getY() - 3, player.getLocation().getZ() - 3))) {
                        if (entity.getType() == EntityType.BLOCK_DISPLAY && entity.getVehicle() == null && entity.getCustomName() != null && entity.getCustomName().equals(bookMeta.getTitle())) {
                            for (Entity passager : entity.getPassengers()) {
                                passager.remove();
                            }
                            config.set(String.valueOf(entity.getUniqueId()),null);
                            entity.remove();
                            message.send(sender,"Модель " + bookMeta.getTitle() + " была удалена");
                            return true;
                        }
                    }
                    message.send(player, "Модель не найдена, встаньте ближе.");
                } else {
                    for (String entry : config.getKeys(false)){ //uuid .name
                        Entity entity =plugin.getServer().getEntity(UUID.fromString(entry));
                        if (entity != null && entity.getName().equals(bookMeta.getTitle())){
                            for (Entity passager : entity.getPassengers()) {
                                passager.remove();
                            }
                            config.set(String.valueOf(entity.getUniqueId()),null);
                            entity.remove();
                            message.send(sender,"Модель " + bookMeta.getTitle() + " была удалена");
                        }
                    }
                }
            } else {
                message.send(sender,"Неправильное использование команды");
            }
        } else {
            logger.info("consol'ka");
        }
        return true;
    }

    public HashMap<ItemStack, Integer> parseMaterials(String command) {
        HashMap<ItemStack, Integer> materials = new HashMap<>();

        // Define the pattern to match block state names
        Pattern pattern = Pattern.compile("Name:\"minecraft:([^\",]+)\"");
        Matcher matcher = pattern.matcher(command);

        // Find all block state names and create ItemStacks for them
        while (matcher.find()) {
            String blockStateName = matcher.group(1);
            Material item = Material.getMaterial(blockStateName.toUpperCase());
            if (item != null) {
                materials.put(new ItemStack(item), materials.getOrDefault(new ItemStack(item), 0) + 1);
            } else {
                logger.info(item + " - is null? blockstatename - " + blockStateName);
            }
        }
        return materials;
    }


    public boolean isValidCommand(String command) {
        if (!command.startsWith("/summon block_display")) {
            return false;
        }

        if (command.contains("CustomName")) {
            return false;
        }

        if (command.contains("Passengers")) {
            Pattern pattern = Pattern.compile("\"id\":\"minecraft:(\\w+)\"");
            Matcher matcher = pattern.matcher(command);

            while (matcher.find()) {
                String id = matcher.group(1);
                if (!id.equals("block_display")) {
                    return false;
                }
            }
            Pattern transformationPattern = Pattern.compile("transformation:\\[(.*?)\\]");
            Matcher transformationMatcher = transformationPattern.matcher(command);

            while (transformationMatcher.find()) {
                String transformationStr = transformationMatcher.group(1);
                String[] transformationValues = transformationStr.split(",");

                for (String valueStr : transformationValues) {
                    float value = Float.parseFloat(valueStr.trim());
                    if (value < -7 || value > 7) {
                        return false;
                    }
                }
            }
        }
        return true;
    }


    public static String request(String url) {
        try {
            if (!url.endsWith("/raw")){
                url = url + "/raw";
            }
            URL obj = new URL(url);

            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                return response.toString();
            } else {
                return null;
            }
        } catch (Exception e) {}
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("model")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                completions.add("pos");
                completions.add("del");
                completions.add("spin");
                return completions;
            } else if (args.length == 2 && (args[0].equals("pos") || args[0].equals("spin"))) {
                List<String> completions = new ArrayList<>();
                completions.add("<model>");
                return completions;
            } else if (args.length == 3 && args[0].equals("pos")) {
                List<String> completions = new ArrayList<>();
                completions.add("<x>");
                return completions;
            } else if (args.length == 4 && args[0].equals("pos")) {
                List<String> completions = new ArrayList<>();
                completions.add("<y>");
                return completions;
            } else if (args.length == 5 && args[0].equals("pos")) {
                List<String> completions = new ArrayList<>();
                completions.add("<z>");
                return completions;
            } else if (args.length == 3 && args[0].equals("spin")){
                List<String> completions = new ArrayList<>();
                completions.add("<yaw>");
                return completions;
            } else if (args.length == 4 && args[0].equals("spin")){
                List<String> completions = new ArrayList<>();
                completions.add("<pitch>");
                return completions;
            }
        }
        return null;
    }
}
