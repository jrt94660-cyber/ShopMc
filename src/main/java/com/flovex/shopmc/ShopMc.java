package com.flovex.shopmc;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.lang.reflect.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.*;

public final class ShopMc extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private FileConfiguration menu;
    private final Map<String,FileConfiguration> cats=new LinkedHashMap<>();
    private final Map<UUID,Buy> buys=new HashMap<>();
    private final Map<UUID,String> views=new HashMap<>();
    private final Map<UUID,Editor> editors=new HashMap<>();
    private static final Pattern HEX=Pattern.compile("&#([0-9a-fA-F]{6})");

    public void onEnable(){
        saveDefaultConfig();
        saveResource("shop/main-shop-gui.yml", false);
        saveResource("basic-setup/messages.yml", false);
        saveResource("basic-setup/sounds.yml", false);
        saveResource("basic-setup/money-format.yml", false);
        saveResource("economy-confirm-gui/vault-confirm.yml", false);
        saveResource("economy-confirm-gui/playerpoints-confirm.yml", false);
        saveResource("economy-confirm-gui/coinsengine-confirm.yml", false);
        ensureCategoryFiles();
        loadMenus();
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand shopCommand = getCommand("shop");
        if (shopCommand == null) {
            getLogger().severe("The /shop command is missing from plugin.yml. Disabling ShopMc.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        shopCommand.setExecutor(this);
        shopCommand.setTabCompleter(this);
        getLogger().info("ShopMc loaded without license or activation code.");
    }
    private FileConfiguration yaml(String p){return YamlConfiguration.loadConfiguration(new File(getDataFolder(),p));}
    private void ensureCategoryFiles() {
        FileConfiguration mainMenu = yaml("shop/main-shop-gui.yml");
        ConfigurationSection categories = mainMenu.getConfigurationSection("categories");
        if (categories == null) return;
        List<String> configured = new ArrayList<>();
        for (String entry : categories.getKeys(false)) {
            ConfigurationSection section = categories.getConfigurationSection(entry);
            if (section == null) continue;
            String category = section.getString("action", entry).toLowerCase(Locale.ROOT);
            if (!configured.contains(category)) configured.add(category);
            String resource = "shop/" + category + ".yml";
            if (!new File(getDataFolder(), resource).exists()) {
                try { saveResource(resource, false); }
                catch (IllegalArgumentException ex) { getLogger().warning("Missing default category file: " + category); }
            }
        }
        getConfig().set("categories", configured);
        saveConfig();
    }
    private void loadMenus() {
        menu = yaml("shop/main-shop-gui.yml");
        cats.clear();
        for (String category : getConfig().getStringList("categories")) {
            String key = category.toLowerCase(Locale.ROOT);
            if (!new File(getDataFolder(), "shop/" + key + ".yml").exists()) {
                getLogger().warning("Configured shop category file missing: " + key);
                continue;
            }
            cats.put(key, yaml("shop/" + key + ".yml"));
        }
    }
    public boolean onCommand(CommandSender s,Command c,String l,String[] a){
        if(a.length>0&&a[0].equalsIgnoreCase("addhanditem")){if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}if(!p.hasPermission("smpshop.admin")){msg(p,"no-permission");return true;}startEditor(p);return true;}
        if(a.length>0&&a[0].equalsIgnoreCase("reload")){if(!s.hasPermission("smpshop.admin")){msg(s,"no-permission");return true;}reloadConfig();ensureCategoryFiles();loadMenus();msg(s,"plugin-reloaded");return true;}
        if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}if(!p.hasPermission("smpshop.use")){msg(p,"no-permission");return true;}
        if(a.length==0){main(p);return true;}
        String key=a[0].toLowerCase(Locale.ROOT);if(cats.containsKey(key)){category(p,key,0);return true;}
        s.sendMessage(col(getConfig().getString("messages.unknown-category","&cUnknown shop category. &7Available: &f%categories%").replace("%categories%",String.join(", ",cats.keySet()))));return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1){String prefix=args[0].toLowerCase(Locale.ROOT);List<String> suggestions=new ArrayList<>();if("reload".startsWith(prefix)&&sender.hasPermission("smpshop.admin"))suggestions.add("reload");if("addhanditem".startsWith(prefix)&&sender.hasPermission("smpshop.admin"))suggestions.add("addhanditem");for(String key:cats.keySet())if(key.startsWith(prefix))suggestions.add(key);return suggestions;}
        return Collections.emptyList();
    }
    private void main(Player p){int rows=limit(menu.getInt("rows",3),1,6);Inventory i=Bukkit.createInventory(null,rows*9,col(menu.getString("title","&8Shop")));ConfigurationSection cs=menu.getConfigurationSection("categories");if(cs!=null)for(String k:cs.getKeys(false)){ConfigurationSection c=cs.getConfigurationSection(k);int slot=c.getInt("slot",0);if(slot>=0&&slot<i.getSize())i.setItem(slot,item(c.getString("material","CHEST"),c.getString("displayname",k),c.getStringList("lore"),c.getBoolean("glow",false)));}fill(i,menu.getConfigurationSection("filler"));open(p,i,"MAIN");}
    private void category(Player p,String key,int page){FileConfiguration c=cats.get(key);if(c==null){p.sendMessage(col(getConfig().getString("messages.unknown-category","&cMissing shop category.")));return;}Inventory i=Bukkit.createInventory(null,limit(c.getInt("rows",3),1,6)*9,col(c.getString("title","&8Shop")));ConfigurationSection items=c.getConfigurationSection("items");List<String> keys=items==null?new ArrayList<>():new ArrayList<>(items.getKeys(false));keys.sort(Comparator.comparingInt(k->items.getInt(k+".slot",999)));int start=page*18,end=Math.min(keys.size(),start+18);for(int n=start;n<end;n++){ConfigurationSection it=items.getConfigurationSection(keys.get(n));int slot=page==0?it.getInt("slot",n%18):n-start;if(slot>=0&&slot<18)i.setItem(slot,shopItem(it));}
        if(page==0)i.setItem(c.getInt("back-button.slot",18),item(c.getString("back-button.material","RED_STAINED_GLASS_PANE"),c.getString("back-button.displayname","&cBack"),c.getStringList("back-button.lore"),false));else i.setItem(c.getInt("last.slot",18),item(c.getString("last.material","ARROW"),c.getString("last.displayname","&cBack"),c.getStringList("last.lore"),false));
        if(end<keys.size())i.setItem(c.getInt("next.slot",26),item(c.getString("next.material","ARROW"),c.getString("next.displayname","&aNext"),c.getStringList("next.lore"),false));fill(i,c.getConfigurationSection("filler"));open(p,i,"CAT:"+key+":"+page);
    }
    private void open(Player p,Inventory i,String tag){p.openInventory(i);views.put(p.getUniqueId(),tag);}
    private ItemStack shopItem(ConfigurationSection c) {
        Material material = mat(c.getString("material", "STONE"));
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(col(c.getString("displayname", "&f" + material.name())));
        List<String> lore = new ArrayList<>();
        for (String line : c.getStringList("lore")) {
            lore.add(col(line.replace("%price%", money(c.getDouble("price", 0)))
                    .replace("%amount%", String.valueOf(c.getInt("amount", 1)))));
        }
        meta.setLore(lore);
        for (String enchantLine : c.getStringList("enchant")) {
            String[] parts = enchantLine.split(":", 2);
            if (parts.length != 2) continue;
            try {
                Enchantment enchantment = Enchantment.getByName(parts[0].trim().toUpperCase(Locale.ROOT));
                int level = Integer.parseInt(parts[1].trim());
                if (enchantment != null && level > 0) meta.addEnchant(enchantment, level, true);
            } catch (RuntimeException ignored) { }
        }
        if (c.getBoolean("glow", false) && meta.getEnchants().isEmpty())
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        if (c.getBoolean("hide-enchants", false)) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||!views.containsKey(p.getUniqueId())||e.getClickedInventory()!=e.getView().getTopInventory())return;e.setCancelled(true);String view=views.get(p.getUniqueId());int slot=e.getRawSlot();
        if(view.startsWith("EDIT:")){editorClick(p,view,slot,e);return;}
        if(view.equals("MAIN")){ConfigurationSection cs=menu.getConfigurationSection("categories");if(cs==null)return;for(String k:cs.getKeys(false)){ConfigurationSection c=cs.getConfigurationSection(k);if(c.getInt("slot",-1)==slot){String action=c.getString("action",k).toLowerCase(Locale.ROOT);String cmd=c.getString("command","").trim();if(!cmd.isEmpty()){p.performCommand(cmd.startsWith("/")?cmd.substring(1):cmd);return;}sound(p,"click");category(p,action,0);return;}}}
        else if(view.startsWith("CAT:")){String[] a=view.split(":");String key=a[1];int page=Integer.parseInt(a[2]);FileConfiguration c=cats.get(key);if(slot==c.getInt("back-button.slot",18)&&page==0){sound(p,"back");main(p);return;}if(slot==c.getInt("last.slot",18)&&page>0){sound(p,"back");category(p,key,page-1);return;}if(slot==c.getInt("next.slot",26)){sound(p,"next");category(p,key,page+1);return;}ConfigurationSection its=c.getConfigurationSection("items");if(its==null)return;List<String> keys=new ArrayList<>(its.getKeys(false));keys.sort(Comparator.comparingInt(k->its.getInt(k+".slot",999)));int start=page*18;for(int n=start;n<Math.min(keys.size(),start+18);n++){ConfigurationSection it=its.getConfigurationSection(keys.get(n));int itemSlot=page==0?it.getInt("slot",n%18):n-start;if(itemSlot==slot){prepare(p,it,key,page);return;}}}
        else if(view.startsWith("CONF:")){Buy b=buys.get(p.getUniqueId());if(b==null)return;if(slot==21){buys.remove(p.getUniqueId());sound(p,"decline");category(p,b.category,b.page);return;}if(slot==23){sound(p,"buy");purchase(p,b);return;}if(slot==9)b.qty=Math.max(1,b.qty-64);else if(slot==10)b.qty=Math.max(1,b.qty-10);else if(slot==11)b.qty=Math.max(1,b.qty-1);else if(slot==15)b.qty=Math.min(b.max,b.qty+1);else if(slot==16)b.qty=Math.min(b.max,b.qty+10);else if(slot==17)b.qty=b.max;sound(p,"click");confirm(p,b);}
    }
    private void prepare(Player p,ConfigurationSection c,String cat,int pg){String provider="vault";ConfigurationSection ep=menu.getConfigurationSection("categories."+cat+".money-provider");String currency=getConfig().getString("economy.default-currency","shards");if(ep!=null){currency=ep.getString("coinsengine-currency",currency);if(ep.getBoolean("playerpoints"))provider="playerpoints";else if(ep.getBoolean("coinsengine"))provider="coinsengine";}if(provider.equals("vault")&&!vaultAvailable()){if(pointsAvailable())provider="coinsengine";else if(getConfig().getString("economy.coinsengine-withdraw-command","").isBlank()){msg(p,"no-economy");return;}}
        Material m=mat(c.getString("material","STONE"));int base=Math.max(1,c.getInt("amount",1));Buy b=new Buy(cat,provider,currency,shopItem(c),c.getDouble("price",0),c.getString("command",""),base,Math.min(64,Math.max(1,m.getMaxStackSize())),pg);b.qty=1;buys.put(p.getUniqueId(),b);confirm(p,b);}
    private void confirm(Player p,Buy b){String file="economy-confirm-gui/"+(b.provider.equals("playerpoints")?"playerpoints":b.provider.equals("coinsengine")?"coinsengine":"vault")+"-confirm.yml";FileConfiguration c=yaml(file);Inventory i=Bukkit.createInventory(null,limit(c.getInt("rows",3),1,6)*9,col(c.getString("title","&8Confirm Purchase")));ItemStack preview=b.icon.clone();ItemMeta pm=preview.getItemMeta();List<String> lore=new ArrayList<>(pm.hasLore()?pm.getLore():List.of());for(String s:c.getStringList("item-purchase.lore"))lore.add(col(s.replace("%amount%",String.valueOf(b.qty*b.base)).replace("%price%",money(b.unit*b.qty)).replace("%item%",ChatColor.stripColor(pm.getDisplayName()))));pm.setLore(lore);preview.setItemMeta(pm);i.setItem(c.getInt("item-purchase.slot",13),preview);
        i.setItem(c.getInt("confirm-purchase.slot",23),item(c.getString("confirm-purchase.material","LIME_STAINED_GLASS_PANE"),c.getString("confirm-purchase.displayname","&aConfirm"),c.getStringList("confirm-purchase.lore"),false));i.setItem(c.getInt("decline-purchase.slot",21),item(c.getString("decline-purchase.material","RED_STAINED_GLASS_PANE"),c.getString("decline-purchase.displayname","&cBack"),c.getStringList("decline-purchase.lore"),false));
        if(b.max>1){for(String key:List.of("add1","add10","setto64","remove64","remove10","remove1")){String sec="if-64max."+key;int s=c.getInt(sec+".slot",-1);if(s>=0)i.setItem(s,item(c.getString(sec+".material","LIME_STAINED_GLASS_PANE"),c.getString(sec+".displayname","&aAdjust"),c.getStringList(sec+".lore"),false));}}p.setMetadata("shopmc-preserve",new org.bukkit.metadata.FixedMetadataValue(this,true));open(p,i,"CONF:"+b.provider);p.removeMetadata("shopmc-preserve",this);}
    private void purchase(Player p,Buy b){double total=b.unit*b.qty;if(b.command.isBlank()&&!canFit(p,b.icon,b.qty*b.base)){msg(p,"inventory-full");return;}boolean paid;if(b.provider.equals("vault"))paid=vaultWithdraw(p,total);else if(b.provider.equals("playerpoints"))paid=pointsTake(p,(int)Math.ceil(total));else{String cmd=getConfig().getString("economy.coinsengine-withdraw-command","");paid=!cmd.isBlank();if(paid)Bukkit.dispatchCommand(Bukkit.getConsoleSender(),replacePurchaseTokens(cmd, p, total, b.qty * b.base, b.currency));}if(!paid){msg(p,"not-enough");return;}
        if(!b.command.isBlank()){for(String cmd:b.command.split("\\|"))Bukkit.dispatchCommand(Bukkit.getConsoleSender(),replacePurchaseTokens(cmd, p, total, b.qty * b.base, b.currency));}else{int remaining=b.qty*b.base;List<ItemStack> rewards=new ArrayList<>();int stackMax=b.icon.getType().getMaxStackSize();while(remaining>0){int n=Math.min(remaining,stackMax);ItemStack reward=b.icon.clone();reward.setAmount(n);rewards.add(reward);remaining-=n;}p.getInventory().addItem(rewards.toArray(new ItemStack[0]));}
        buys.remove(p.getUniqueId());Map<String,String> vars=Map.of("%amount%",String.valueOf(b.qty*b.base),"%item%",ChatColor.stripColor(b.icon.getItemMeta().getDisplayName()),"%price%",money(total));sendMessageChannels(p,"successful-purchase",vars);category(p,b.category,b.page);}

    @EventHandler public void editorChat(AsyncPlayerChatEvent e){
        Editor ed=editors.get(e.getPlayer().getUniqueId());if(ed==null||ed.stage==null)return;
        e.setCancelled(true);String input=e.getMessage().trim();
        Bukkit.getScheduler().runTask(this,()->{Player p=e.getPlayer();
            if(input.equalsIgnoreCase("cancel")){startEditor(p);return;}
            if(ed.stage.equals("category-name")){if(input.isBlank()||input.length()>32){p.sendMessage(col("&cName must be 1-32 characters. Try again or type cancel."));return;}ed.newCategory=input;ed.stage=null;openCategorySlotPicker(p);return;}
            if(ed.stage.equals("item-price")){try{double price=Double.parseDouble(input.replace(",",""));if(!Double.isFinite(price)||price<0)throw new NumberFormatException();ed.price=price;ed.stage=null;openItemDone(p);}catch(NumberFormatException ex){p.sendMessage(col("&cEnter a valid non-negative price, or type cancel."));}}
        });
    }
    private void startEditor(Player p){Editor ed=new Editor();editors.put(p.getUniqueId(),ed);Inventory i=Bukkit.createInventory(null,27,col("&8Shop Editor"));i.setItem(11,item("EMERALD","&aNew Category",List.of("&7Create a main shop category"),false));i.setItem(15,item("PAPER","&eNew Item",List.of("&7Add your held item to a category"),false));i.setItem(22,item("BARRIER","&cClose",List.of(),false));open(p,i,"EDIT:MAIN");}
    private void openCategorySlotPicker(Player p){Inventory i=Bukkit.createInventory(null,27,col("&8Choose Main Menu Slot"));FileConfiguration m=yaml("shop/main-shop-gui.yml");ConfigurationSection cs=m.getConfigurationSection("categories");Set<Integer> occupied=new HashSet<>();if(cs!=null)for(String k:cs.getKeys(false))occupied.add(cs.getInt(k+".slot",-1));for(int n=0;n<27;n++)i.setItem(n,item(occupied.contains(n)?"RED_STAINED_GLASS_PANE":"LIME_STAINED_GLASS_PANE",occupied.contains(n)?"&cOccupied Slot "+n:"&aSlot "+n,List.of("&7Click to choose this slot"),false));open(p,i,"EDIT:CAT_SLOT");}
    private void openCategorySelect(Player p){Inventory i=Bukkit.createInventory(null,27,col("&8Choose Category"));ConfigurationSection cs=menu.getConfigurationSection("categories");int slot=0;if(cs!=null)for(String k:cs.getKeys(false)){ConfigurationSection c=cs.getConfigurationSection(k);if(slot<27)i.setItem(slot,item(c.getString("material","CHEST"),c.getString("displayname",k),List.of("&7Add item to this category"),false));slot++;}open(p,i,"EDIT:ITEM_CAT");}
    private void openItemSlotPicker(Player p){Editor ed=editors.get(p.getUniqueId());FileConfiguration c=cats.get(ed.category);Inventory i=Bukkit.createInventory(null,27,col("&8Choose Item Slot"));ConfigurationSection its=c.getConfigurationSection("items");Set<Integer> occupied=new HashSet<>();if(its!=null)for(String k:its.getKeys(false))occupied.add(its.getInt(k+".slot",-1));for(int n=0;n<18;n++)i.setItem(n,item(occupied.contains(n)?"RED_STAINED_GLASS_PANE":"LIME_STAINED_GLASS_PANE",occupied.contains(n)?"&cOccupied Slot "+n:"&aSlot "+n,List.of("&7Click to choose this slot"),false));i.setItem(22,item("BARRIER","&cCancel",List.of(),false));open(p,i,"EDIT:ITEM_SLOT");}
    private void openItemDone(Player p){Editor ed=editors.get(p.getUniqueId());Inventory i=Bukkit.createInventory(null,27,col("&8Review New Item"));ItemStack preview=ed.hand.clone();preview.setAmount(1);i.setItem(13,preview);i.setItem(11,item("RED_STAINED_GLASS_PANE","&cCancel",List.of("&7Cancel"),false));i.setItem(15,item("LIME_STAINED_GLASS_PANE","&aDone",List.of("&7Price: &f"+money(ed.price),"&7Save item to shop"),false));open(p,i,"EDIT:ITEM_DONE");}
    private void editorClick(Player p,String view,int slot,InventoryClickEvent e){e.setCancelled(true);Editor ed=editors.get(p.getUniqueId());if(ed==null)return;
        if(view.equals("EDIT:MAIN")){if(slot==11){ed.stage="category-name";p.closeInventory();views.remove(p.getUniqueId());p.sendMessage(col("&eType the category name in chat. Type &ccancel &eto abort."));}else if(slot==15){ItemStack held=p.getInventory().getItemInMainHand();if(held==null||held.getType()==Material.AIR){p.sendMessage(col("&cHold the item you want to add in your main hand."));return;}ed.hand=held.clone();openCategorySelect(p);}else if(slot==22){editors.remove(p.getUniqueId());p.closeInventory();}}
        else if(view.equals("EDIT:CAT_SLOT")){if(slot<0||slot>=27)return;FileConfiguration m=yaml("shop/main-shop-gui.yml");ConfigurationSection cs=m.getConfigurationSection("categories");if(cs!=null)for(String k:cs.getKeys(false))if(cs.getInt(k+".slot",-1)==slot){p.sendMessage(col("&cThat slot is occupied."));return;}createCategory(p,slot);}
        else if(view.equals("EDIT:ITEM_CAT")){ConfigurationSection cs=menu.getConfigurationSection("categories");if(cs==null)return;int n=0;for(String k:cs.getKeys(false)){if(n++==slot){ed.category=cs.getConfigurationSection(k).getString("action",k).toLowerCase(Locale.ROOT);openItemSlotPicker(p);return;}}}
        else if(view.equals("EDIT:ITEM_SLOT")){if(slot==22){startEditor(p);return;}if(slot<0||slot>=18)return;FileConfiguration c=cats.get(ed.category);ConfigurationSection its=c.getConfigurationSection("items");if(its!=null)for(String k:its.getKeys(false))if(its.getInt(k+".slot",-1)==slot){p.sendMessage(col("&cThat slot is occupied."));return;}ed.itemSlot=slot;ed.stage="item-price";p.closeInventory();views.remove(p.getUniqueId());p.sendMessage(col("&eEnter the item price in chat. Type &ccancel &eto cancel."));}
        else if(view.equals("EDIT:ITEM_DONE")){if(slot==11)startEditor(p);else if(slot==15){saveHandItem(p);startEditor(p);}}
    }
    private void createCategory(Player p,int slot){Editor ed=editors.get(p.getUniqueId());String key=ed.newCategory.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");if(key.isBlank()||cats.containsKey(key)){p.sendMessage(col("&cInvalid or duplicate category name."));startEditor(p);return;}
        FileConfiguration main=yaml("shop/main-shop-gui.yml");String base="categories."+key;main.set(base+".material",ed.hand==null?"CHEST":ed.hand.getType().name());main.set(base+".displayname","&a"+ed.newCategory);main.set(base+".slot",slot);main.set(base+".action",key);main.set(base+".premium",false);main.set(base+".glow",false);main.set(base+".money-provider.vault",true);main.set(base+".money-provider.playerpoints",false);main.set(base+".money-provider.coinsengine",false);main.set(base+".money-provider.coinsengine-currency","");main.set(base+".lore",List.of("&7Click to view"));saveYaml(main,"shop/main-shop-gui.yml");
        FileConfiguration cat=new YamlConfiguration();cat.set("title","&8Shop - "+ed.newCategory);cat.set("rows",3);cat.set("back-button.slot",18);cat.set("back-button.material","RED_STAINED_GLASS_PANE");cat.set("back-button.displayname","&cBack");cat.set("back-button.lore",List.of("&7Click to return"));cat.set("next.slot",26);cat.set("next.material","ARROW");cat.set("next.displayname","&aNext");cat.set("next.lore",List.of("&7Next page"));cat.set("last.slot",18);cat.set("last.material","ARROW");cat.set("last.displayname","&cBack");cat.set("last.lore",List.of("&7Previous page"));cat.set("filler.enabled",false);cat.set("items",new LinkedHashMap<>());saveYaml(cat,"shop/"+key+".yml");
        List<String> names=new ArrayList<>(getConfig().getStringList("categories"));if(!names.contains(key))names.add(key);getConfig().set("categories",names);saveConfig();loadMenus();p.sendMessage(col("&aCategory &f"+ed.newCategory+"&a created at main-menu slot &f"+slot+"&a."));startEditor(p);}
    private void saveHandItem(Player p){Editor ed=editors.get(p.getUniqueId());FileConfiguration cat=cats.get(ed.category);if(cat==null||ed.hand==null)return;ConfigurationSection its=cat.getConfigurationSection("items");int count=its==null?0:its.getKeys(false).size();String key=ed.hand.getType().name().toLowerCase(Locale.ROOT)+"-"+(count+1);while(cat.contains("items."+key))key+="-";
        ItemStack hand=ed.hand.clone();ItemMeta meta=hand.getItemMeta();cat.set("items."+key+".slot",ed.itemSlot);cat.set("items."+key+".material",hand.getType().name());cat.set("items."+key+".displayname",meta!=null&&meta.hasDisplayName()?meta.getDisplayName():"&f"+hand.getType().name().replace('_',' '));cat.set("items."+key+".price",ed.price);cat.set("items."+key+".amount",1);cat.set("items."+key+".command","");cat.set("items."+key+".lore",List.of("&fBuy Price: &#0bf52b$%price%"));if(meta!=null){List<String> ench=new ArrayList<>();for(var en:meta.getEnchants().entrySet())ench.add(en.getKey().getName()+":"+en.getValue());if(!ench.isEmpty())cat.set("items."+key+".enchant",ench);}saveYaml(cat,"shop/"+ed.category+".yml");loadMenus();p.sendMessage(col("&aAdded item to &f"+ed.category+"&a slot &f"+ed.itemSlot+"&a priced &f"+money(ed.price)+"&a.");}
    private void saveYaml(FileConfiguration cfg,String path){try{cfg.save(new File(getDataFolder(),path));}catch(Exception ex){getLogger().severe("Could not save "+path+": "+ex.getMessage());}}
    private static class Editor{String stage,newCategory,category;ItemStack hand;int itemSlot=-1;double price;}

    @EventHandler public void close(InventoryCloseEvent e){if(e.getPlayer() instanceof Player p)views.remove(p.getUniqueId());}
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent e){views.remove(e.getPlayer().getUniqueId());buys.remove(e.getPlayer().getUniqueId());editors.remove(e.getPlayer().getUniqueId());}
    private ItemStack item(String material,String name,List<String> lore,boolean glow){ItemStack i=new ItemStack(mat(material));ItemMeta m=i.getItemMeta();m.setDisplayName(col(name));m.setLore(lore.stream().map(this::col).toList());if(glow)m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,1,true);m.addItemFlags(ItemFlag.HIDE_ENCHANTS);i.setItemMeta(m);return i;}
    private Material mat(String name){Material m=Material.matchMaterial(name==null?"STONE":name);return m==null?Material.STONE:m;}
    private void fill(Inventory i,ConfigurationSection c){if(c==null||!c.getBoolean("enabled",false))return;ItemStack pane=item(c.getString("material","GRAY_STAINED_GLASS_PANE"),c.getString("displayname","&7 "),c.getStringList("lore"),false);for(int n=0;n<i.getSize();n++)if(i.getItem(n)==null)i.setItem(n,pane);}
    private boolean vaultAvailable(){try{Class<?> e=Class.forName("net.milkbowl.vault.economy.Economy");return Bukkit.getServicesManager().getRegistration(e)!=null;}catch(Throwable t){return false;}}
    private boolean vaultWithdraw(Player p,double v){try{Class<?> ec=Class.forName("net.milkbowl.vault.economy.Economy");Object reg=Bukkit.getServicesManager().getRegistration(ec);Object provider=reg.getClass().getMethod("getProvider").invoke(reg);Object r=provider.getClass().getMethod("withdrawPlayer",OfflinePlayer.class,double.class).invoke(provider,p,v);return (boolean)r.getClass().getMethod("transactionSuccess").invoke(r);}catch(Throwable t){return false;}}
    private void vaultDeposit(Player p,double v){try{Class<?> ec=Class.forName("net.milkbowl.vault.economy.Economy");Object reg=Bukkit.getServicesManager().getRegistration(ec);Object provider=reg.getClass().getMethod("getProvider").invoke(reg);provider.getClass().getMethod("depositPlayer",OfflinePlayer.class,double.class).invoke(provider,p,v);}catch(Throwable ignored){}}
    private boolean pointsAvailable(){return Bukkit.getPluginManager().getPlugin("PlayerPoints")!=null;}
    private boolean pointsTake(Player p,int v){return pointsCall(p,v,"take");}private void pointsGive(Player p,int v){pointsCall(p,v,"give");}
    private boolean pointsCall(Player p,int v,String method){try{Object plugin=Bukkit.getPluginManager().getPlugin("PlayerPoints");Object api=plugin.getClass().getMethod("getAPI").invoke(plugin);for(Method m:api.getClass().getMethods())if(m.getName().equals(method)&&m.getParameterCount()==2){Class<?>[] t=m.getParameterTypes();Object arg=t[0]==UUID.class?p.getUniqueId():p;if(t[1]==int.class){Object result=m.invoke(api,arg,v);return m.getReturnType()==void.class||Boolean.TRUE.equals(result);}}return false;}catch(Throwable t){return false;}}
    private boolean canFit(Player p,ItemStack base,int amount){int remaining=amount;for(ItemStack s:p.getInventory().getStorageContents()){if(s==null){remaining-=base.getType().getMaxStackSize();}else if(s.isSimilar(base)){remaining-=s.getMaxStackSize()-s.getAmount();}if(remaining<=0)return true;}return remaining<=0;}
    private String replacePurchaseTokens(String command, Player player, double price, int amount, String currency) {
        return command.replace("%player%", player.getName()).replace("{player}", player.getName())
                .replace("%amount%", String.valueOf(amount)).replace("{amount}", String.valueOf(amount))
                .replace("%price%", plainNumber(price)).replace("{price}", plainNumber(price))
                .replace("%currency%", currency == null ? "" : currency)
                .replace("{currency}", currency == null ? "" : currency);
    }
    private String plainNumber(double v){return new DecimalFormat("0.##").format(v);}
    private String money(double n){FileConfiguration f=yaml("basic-setup/money-format.yml");String suffix="";if(n>=1e12){n/=1e12;suffix=f.getString("format.t","T");}else if(n>=1e9){n/=1e9;suffix=f.getString("format.b","B");}else if(n>=1e6){n/=1e6;suffix=f.getString("format.m","M");}else if(n>=1e3){n/=1e3;suffix=f.getString("format.k","K");}return new DecimalFormat(f.getBoolean("format.show-2-0",true)?"#,##0.00":"#,##0.##").format(n)+suffix;}
    private String col(String s){if(s==null)return "";Matcher m=HEX.matcher(s);StringBuffer b=new StringBuffer();while(m.find()){String h=m.group(1);String repl="§x§"+h.charAt(0)+"§"+h.charAt(1)+"§"+h.charAt(2)+"§"+h.charAt(3)+"§"+h.charAt(4)+"§"+h.charAt(5);m.appendReplacement(b,repl);}m.appendTail(b);return ChatColor.translateAlternateColorCodes('&',b.toString());}
    private void msg(CommandSender s,String k){FileConfiguration m=yaml("basic-setup/messages.yml");String path="messages."+k+".module.";boolean sent=false;if(m.getBoolean(path+"chat.enabled",false)){for(String line:m.getStringList(path+"chat.message")){s.sendMessage(col(line));sent=true;}}if(s instanceof Player p){if(m.getBoolean(path+"actionbar.enabled",false)){p.sendActionBar(col(m.getString(path+"actionbar.message","")));sent=true;}if(m.getBoolean(path+"title.enabled",false)){p.sendTitle(col(m.getString(path+"title.title","")),col(m.getString(path+"title.subtitle","")),10,40,10);sent=true;}}if(!sent)s.sendMessage(col(getConfig().getString("messages."+k,"&cError.")));}
    private void sendMessageChannels(Player p,String key,Map<String,String> vars){FileConfiguration m=yaml("basic-setup/messages.yml");String path="messages."+key+".module.";boolean sent=false;if(m.getBoolean(path+"chat.enabled",false)){for(String line:m.getStringList(path+"chat.message")){for(var v:vars.entrySet())line=line.replace(v.getKey(),v.getValue());p.sendMessage(col(line));sent=true;}}if(m.getBoolean(path+"actionbar.enabled",false)){String line=m.getString(path+"actionbar.message","");for(var v:vars.entrySet())line=line.replace(v.getKey(),v.getValue());p.sendActionBar(col(line));sent=true;}if(m.getBoolean(path+"title.enabled",false)){String title=m.getString(path+"title.title",""),sub=m.getString(path+"title.subtitle","");for(var v:vars.entrySet()){title=title.replace(v.getKey(),v.getValue());sub=sub.replace(v.getKey(),v.getValue());}p.sendTitle(col(title),col(sub),10,40,10);sent=true;}if(!sent)p.sendMessage(col(getConfig().getString("messages.purchase","&aPurchase complete.").replace("%amount%",vars.getOrDefault("%amount%","")).replace("%item%",vars.getOrDefault("%item%","")).replace("%price%",vars.getOrDefault("%price%",""))));}
    private void sound(Player p,String key){FileConfiguration c=yaml("basic-setup/sounds.yml");String path="sounds."+key;if(!c.getBoolean(path+".enabled",true))return;try{p.playSound(p.getLocation(),Sound.valueOf(c.getString(path+".sound","UI_BUTTON_CLICK")),(float)c.getDouble(path+".volume",1),(float)c.getDouble(path+".pitch",1));}catch(Exception ignored){}}
    private int limit(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    private static class Buy{String category,provider,command,currency;ItemStack icon;double unit;int base,qty=1,max,page;Buy(String c,String p,String cur,ItemStack i,double price,String cmd,int ba,int mx,int pg){category=c;provider=p;currency=cur;icon=i;unit=price;command=cmd;base=ba;max=mx;page=pg;}}
}
