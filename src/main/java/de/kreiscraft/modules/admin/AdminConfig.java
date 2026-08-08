package de.kreiscraft.modules.admin;

import java.net.URI;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record AdminConfig(Links links, String timezone) {

    public AdminConfig {
        if (links == null) {
            links = new Links(URI.create("https://kreiscraft.de"), URI.create("https://discord.gg/example"),
                    URI.create("https://youtube.com/@example"));
        }
        if (timezone == null) {
            timezone = "UTC";
        }
    }

    @ConfigSerializable
    public record Links(URI website, URI discord, URI youtube) {

        public Links {
            if (website == null) {
                website = URI.create("https://kreiscraft.de");
            }
            if (discord == null) {
                discord = URI.create("https://discord.gg/example");
            }
            if (youtube == null) {
                youtube = URI.create("https://youtube.com/@example");
            }
        }
    }
}
