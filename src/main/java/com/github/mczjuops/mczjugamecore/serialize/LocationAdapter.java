package com.github.mczjuops.mczjugamecore.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.IOException;
import java.lang.reflect.Modifier;

public class LocationAdapter extends TypeAdapter<Location> {

    public static Gson getGsonBuilder(){
        return new GsonBuilder()
                .excludeFieldsWithModifiers(
                        Modifier.PRIVATE,
                        Modifier.PROTECTED,
                        Modifier.STATIC,
                        Modifier.TRANSIENT,
                        Modifier.FINAL
                )
                .registerTypeAdapter(Location.class, new LocationAdapter())
                .setPrettyPrinting()
                .create();
    }

    @Override
    public void write(JsonWriter out, Location loc) throws IOException {
        if (loc == null) {
            out.nullValue();
            return;
        }

        out.beginObject();
        out.name("world").value(loc.getWorld().getName());
        out.name("x").value(loc.getX());
        out.name("y").value(loc.getY());
        out.name("z").value(loc.getZ());
        out.name("yaw").value(loc.getYaw());
        out.name("pitch").value(loc.getPitch());
        out.endObject();
    }

    @Override
    public Location read(JsonReader in) throws IOException {
        String world = null;
        double x = 0, y = 0, z = 0;
        float yaw = 0, pitch = 0;

        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "world": world = in.nextString(); break;
                case "x": x = in.nextDouble(); break;
                case "y": y = in.nextDouble(); break;
                case "z": z = in.nextDouble(); break;
                case "yaw": yaw = (float) in.nextDouble(); break;
                case "pitch": pitch = (float) in.nextDouble(); break;
            }
        }
        in.endObject();

        return new Location(
                Bukkit.getWorld(world),
                x, y, z,
                yaw, pitch
        );
    }
}
