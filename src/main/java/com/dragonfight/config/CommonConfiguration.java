package com.dragonfight.config;

import com.cupboard.config.ICommonConfig;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class CommonConfiguration implements ICommonConfig
{
    public int          dragonDifficulty           = 2;
    public boolean      printDragonPhases          = false;
    public boolean      disableDragonAreaSpawns    = true;
    public List<String> spawnoncrystaldestroy      = Lists.newArrayList("minecraft:phantom");
    public List<String> spawnoncrystalrespawn      = Lists.newArrayList("minecraft:blaze");
    public List<String> crystalPendingRespawns     = Lists.newArrayList();
    public List<String> spawnwhilelanded           = Lists.newArrayList("minecraft:enderman");
    public double       crystalRespawnTimeModifier = 1.0;
    public double       lightningExplosionDensity  = 1.0;
    public boolean      disableLightning           = false;
    public boolean      antiflightAbility          = true;
    public double       dragonHealthModifier       = 1.0;
    public double       dragonDamageModifier       = 1.0;
    public double       mobSpawnAmountModifier     = 1.0;
    public double       dragonXPModifier           = 1.0;

    public CommonConfiguration()
    {
    }

    public JsonObject serialize()
    {
        final JsonObject root = new JsonObject();

        final JsonObject entry = new JsonObject();
        entry.addProperty("desc:", "Sets the dragon difficulty modifier, the higher the more difficult the dragon gets."
                                     + "Scales up mob spawn amount, dragon damage and health aswell as crystal respawn intervals. Note that the difficulty already scales on the playercount involved in the fight, this is a static bonus ontop."
                                     + "default:2, vanilla:0");
        entry.addProperty("dragonDifficulty", dragonDifficulty);
        root.add("dragonDifficulty", entry);

        final JsonObject entry8 = new JsonObject();
        entry8.addProperty("desc:", "Modifies crystal respawn time, 0.5 = spawns twice as fast, 2 = twice as slow. default:1.0");
        entry8.addProperty("crystalRespawnTimeModifier", crystalRespawnTimeModifier);
        root.add("crystalRespawnTimeModifier", entry8);

        final JsonObject entry9 = new JsonObject();
        entry9.addProperty("desc:", "Modifies lightning and explosion density, 0.5 = half as many, 2 = twice as many. default:1.0");
        entry9.addProperty("lightningExplosionDensity", lightningExplosionDensity);
        root.add("lightningExplosionDensity", entry9);

        final JsonObject entry10 = new JsonObject();
        entry10.addProperty("desc:", "Disables lightning spawns: default:false");
        entry10.addProperty("disableLightning", disableLightning);
        root.add("disableLightning", entry10);

        final JsonObject entry15 = new JsonObject();
        entry15.addProperty("desc:", "Enables anti-flight ability: default:true");
        entry15.addProperty("antiflightAbility", antiflightAbility);
        root.add("antiflightAbility", entry15);

        final JsonObject entry16 = new JsonObject();
        entry16.addProperty("desc:", "Sets the dragon health modifier: default:1.0");
        entry16.addProperty("dragonHealthModifier", dragonHealthModifier);
        root.add("dragonHealthModifier", entry16);

        final JsonObject entry17 = new JsonObject();
        entry17.addProperty("desc:", "Sets the dragon damage modifier: default:1.0");
        entry17.addProperty("dragonDamageModifier", dragonDamageModifier);
        root.add("dragonDamageModifier", entry17);

        final JsonObject entry18 = new JsonObject();
        entry18.addProperty("desc:", "Sets the mob spawn amount modifier: default:1.0");
        entry18.addProperty("mobSpawnAmountModifier", mobSpawnAmountModifier);
        root.add("mobSpawnAmountModifier", entry18);

        final JsonObject entry20 = new JsonObject();
        entry20.addProperty("desc:", "Sets the XP drop modifier: default:1.0");
        entry20.addProperty("dragonXPModifier", dragonXPModifier);
        root.add("dragonXPModifier", entry20);

        final JsonObject entry2 = new JsonObject();
        entry2.addProperty("desc:", "Prints the dragon phase in chat if enabled: default:false");
        entry2.addProperty("printDragonPhases", printDragonPhases);
        root.add("printDragonPhases", entry2);

        final JsonObject entry3 = new JsonObject();
        entry3.addProperty("desc:", "Disables mob spawning on the Dragon island during the fight: default:true");
        entry3.addProperty("disableDragonAreaSpawns", disableDragonAreaSpawns);
        root.add("disableDragonAreaSpawns", entry3);

        root.addProperty("descSpawnEntries",
          "Below are configuration options for entity spawning, when trying to use nbt append it right after the entity type, e.g. minecraft:zombie{}. Put a backslash in front of all quotation marks");

        final JsonObject entry4 = new JsonObject();
        entry4.addProperty("desc:",
          "List of mobs spawning when a crystal is destroyed at range, intended to be flying or ranged to close the gap: e.g. format :  [\"minecraft:zombie\", \"minecraft:creeper\"]");
        final JsonArray list4 = new JsonArray();
        for (final String name : spawnoncrystaldestroy)
        {
            list4.add(name);
        }
        entry4.add("spawnoncrystaldestroy", list4);
        root.add("spawnoncrystaldestroy", entry4);

        final JsonObject entry5 = new JsonObject();
        entry5.addProperty("desc:",
          "List of mobs spawning on crystal respawn, intended to be ranged to ward of players from a distance: e.g. format :  [\"minecraft:zombie\", \"minecraft:creeper\"]");
        final JsonArray list5 = new JsonArray();
        for (final String name : spawnoncrystalrespawn)
        {
            list5.add(name);
        }
        entry5.add("spawnoncrystalrespawn", list5);
        root.add("spawnoncrystalrespawn", entry5);

        final JsonObject entry6 = new JsonObject();
        entry6.addProperty("desc:",
          "List of mobs spawning while the dragon is sitting in the middle, intended to be melee and not vulnerable to ranged: e.g. format :  [\"minecraft:zombie\", \"minecraft:creeper\"]");
        final JsonArray list6 = new JsonArray();
        for (final String name : spawnwhilelanded)
        {
            list6.add(name);
        }
        entry6.add("spawnwhilelanded", list6);
        root.add("spawnwhilelanded", entry6);

        final JsonObject entry19 = new JsonObject();
        entry19.addProperty("desc:",
          "List of crystal respawn locations for the end crystals, filled automatically on destroy recommended to not touch. XYZ coords seperated by semicolon");
        final JsonArray list19 = new JsonArray();
        for (final String name : crystalPendingRespawns)
        {
            list19.add(name);
        }
        entry19.add("crystalPendingRespawns", list19);
        root.add("crystalPendingRespawns", entry19);

        return root;
    }

    public void deserialize(JsonObject data)
    {
        dragonDifficulty = data.get("dragonDifficulty").getAsJsonObject().get("dragonDifficulty").getAsInt();
        crystalRespawnTimeModifier = data.get("crystalRespawnTimeModifier").getAsJsonObject().get("crystalRespawnTimeModifier").getAsDouble();
        lightningExplosionDensity = data.get("lightningExplosionDensity").getAsJsonObject().get("lightningExplosionDensity").getAsDouble();
        dragonHealthModifier = data.get("dragonHealthModifier").getAsJsonObject().get("dragonHealthModifier").getAsDouble();
        dragonDamageModifier = data.get("dragonDamageModifier").getAsJsonObject().get("dragonDamageModifier").getAsDouble();
        dragonXPModifier = data.get("dragonXPModifier").getAsJsonObject().get("dragonXPModifier").getAsDouble();
        mobSpawnAmountModifier = data.get("mobSpawnAmountModifier").getAsJsonObject().get("mobSpawnAmountModifier").getAsDouble();
        printDragonPhases = data.get("printDragonPhases").getAsJsonObject().get("printDragonPhases").getAsBoolean();
        antiflightAbility = data.get("antiflightAbility").getAsJsonObject().get("antiflightAbility").getAsBoolean();
        disableLightning = data.get("disableLightning").getAsJsonObject().get("disableLightning").getAsBoolean();
        disableDragonAreaSpawns = data.get("disableDragonAreaSpawns").getAsJsonObject().get("disableDragonAreaSpawns").getAsBoolean();
        spawnoncrystaldestroy = new ArrayList<>();
        for (final JsonElement element : data.get("spawnoncrystaldestroy").getAsJsonObject().get("spawnoncrystaldestroy").getAsJsonArray())
        {
            spawnoncrystaldestroy.add(element.getAsString());
        }

        spawnoncrystalrespawn = new ArrayList<>();
        for (final JsonElement element : data.get("spawnoncrystalrespawn").getAsJsonObject().get("spawnoncrystalrespawn").getAsJsonArray())
        {
            spawnoncrystalrespawn.add(element.getAsString());
        }
        spawnwhilelanded = new ArrayList<>();
        for (final JsonElement element : data.get("spawnwhilelanded").getAsJsonObject().get("spawnwhilelanded").getAsJsonArray())
        {
            spawnwhilelanded.add(element.getAsString());
        }

        crystalPendingRespawns = new ArrayList<>();
        for (final JsonElement element : data.get("crystalPendingRespawns").getAsJsonObject().get("crystalPendingRespawns").getAsJsonArray())
        {
            crystalPendingRespawns.add(element.getAsString());
        }


        ConfigurationCache.onConfigChanged();
    }
}
