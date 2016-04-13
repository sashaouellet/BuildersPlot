package me.firefly.BuildersPlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

@SerializableAs("Player Properties")
public class PlayerProperties implements ConfigurationSerializable {
    String playerName;
    Map<String, Zone> zones = new HashMap();

    public PlayerProperties(String playerName) {
        this.playerName = playerName;
    }

    public void addZone(Zone zone) {
        this.zones.put(zone.getName(), zone);
    }

    private void addNullZone(String name) {
        this.zones.put(name, null);
    }

    public void removeZone(String name) {
        this.zones.remove(name);
    }

    public List<Zone> getZones() {
        List<Zone> zoneList = new ArrayList();
        for (Zone z : this.zones.values()) {
            zoneList.add(z);
        }
        return zoneList;
    }

    public boolean isAllowedAt(Location location) {
        for (Zone zone : this.zones.values()) {
            if ((zone != null) && (zone.isInZone(location))) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap();
        map.put("Name", this.playerName);
        map.put("Zones", new ArrayList(this.zones.keySet()));
        return map;
    }

    public static PlayerProperties deserialize(Map<String, Object> map) {
        String playerName = (String)map.get("Name");
        List<String> zones = (List)map.get("Zones");

        PlayerProperties properties = new PlayerProperties(playerName);
        for (String zone : zones) {
            properties.addNullZone(zone);
        }
        return properties;
    }

    public void resetZones(Map<String, Zone> zoneList) {
        for (Iterator<Map.Entry<String, Zone>> i = this.zones.entrySet().iterator(); i.hasNext();) {
            Map.Entry<String, Zone> e = (Map.Entry)i.next();
            Zone z = (Zone)zoneList.get(e.getKey());
            if (z != null) {
                e.setValue(z);
            } else {
                i.remove();
            }
        }
    }
}
