package me.firefly.BuildersPlot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

@SerializableAs("Zone")
public class Zone implements ConfigurationSerializable {
    int x1;
    int x2;
    int y1;
    int y2;
    int z1;
    int z2;
    String world;
    String name;
    String owner;
    List<String> friends;

    public Zone(String name, String owner, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.world = world;
        this.owner = owner;
        this.name = name;
    }

    public Zone(String name, String owner, List<String> members, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.world = world;
        this.owner = owner;
        this.name = name;
        this.friends = members;
    }

    public void addMember(String m) {
        this.friends.add(m);
    }

    public void removeMember(String m) {
        this.friends.remove(m);
    }

    public String getName() {
        return this.name;
    }

    public String getOwner() {
        return this.owner;
    }

    public World getWorld() {
        World worldObj = Bukkit.getWorld(this.world);
        return worldObj;
    }

    public int getX1() {
        return this.x1;
    }

    public int getY1() {
        return this.y1;
    }

    public int getZ1() {
        return this.z1;
    }

    public int getX2() {
        return this.x2;
    }

    public int getY2() {
        return this.y2;
    }

    public int getZ2() {
        return this.z2;
    }

    public boolean isInZone(Location location) {
        if (!location.getWorld().getName().equals(this.world)) {
            return false;
        }
        int posX = location.getBlockX();
        int posY = location.getBlockY();
        int posZ = location.getBlockZ();
        if (this.x1 < this.x2) {
            if ((posX < this.x1) || (this.x2 < posX)) {
                return false;
            }
        } else if ((posX < this.x2) || (this.x1 < posX)) {
            return false;
        }
        if (this.z1 < this.z2) {
            if ((posZ < this.z1) || (this.z2 < posZ)) {
                return false;
            }
        } else if ((posZ < this.z2) || (this.z1 < posZ)) {
            return false;
        }
        if (this.y1 < this.y2) {
            if ((posY < this.y1) || (this.y2 < posY)) {
                return false;
            }
        } else if ((posY < this.y2) || (this.y1 < posY)) {
            return false;
        }
        return true;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap();
        map.put("Name", this.name);
        map.put("Owner", this.owner);
        map.put("World", this.world);
        map.put("x1", Integer.valueOf(this.x1));
        map.put("y1", Integer.valueOf(this.y1));
        map.put("z1", Integer.valueOf(this.z1));
        map.put("x2", Integer.valueOf(this.x2));
        map.put("y2", Integer.valueOf(this.y2));
        map.put("z2", Integer.valueOf(this.z2));
        map.put("Friends List", this.friends);

        return map;
    }

    public static Zone deserialize(Map<String, Object> map) {
        String name = (String)map.get("Name");
        String owner = (String)map.get("Owner");
        String world = (String)map.get("World");
        int x1 = ((Integer)map.get("x1")).intValue();
        int y1 = ((Integer)map.get("y1")).intValue();
        int z1 = ((Integer)map.get("z1")).intValue();
        int x2 = ((Integer)map.get("x2")).intValue();
        int y2 = ((Integer)map.get("y2")).intValue();
        int z2 = ((Integer)map.get("z2")).intValue();
        List<String> friends = (List)map.get("Friends List");

        Zone zone = new Zone(name, owner, friends, world, x1, y1, z1, x2, y2, z2);
        return zone;
    }
}
