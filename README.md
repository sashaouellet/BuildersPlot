## Purpose
This is a plugin created for Bukkit, [a commonly used Minecraft Server installation with its own API](https://hub.spigotmc.org/javadocs/bukkit/) to control certain aspects server-side as well as listen to player events. The plugin hooks into this API to provide the ability for server moderators to create 3-dimensional regions called plots that can be individually named, and then claimed, by other users on the server.

Users can only build and destroy in the plots they claim, solving the problem of "griefing" many servers face.

## How it Works
The plugin customizes what actions are limited to plots through a config file. By having event listeners, the plugin can track the actions of players in order to determine if what they are doing is allowed **where** they are doing it. Plot objects and the properties of individual players as they pertain to plots are serialized into their own individual YAML files for data storage. These objects are also deserializable.
