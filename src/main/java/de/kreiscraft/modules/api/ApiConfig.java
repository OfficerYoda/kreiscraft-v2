package de.kreiscraft.modules.api;

import java.time.Duration;
import java.util.List;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable public record ApiConfig(String host,int port,List<String>corsOrigins,SessionCookie sessionCookie,Duration heartbeatInterval,Duration asyncTimeout,Duration metricsRefreshInterval,Duration verificationTimeout,String adminApiKey){

public ApiConfig{if(host==null||host.isBlank())host="0.0.0.0";if(port<=0)port=8080;if(corsOrigins==null||corsOrigins.isEmpty())corsOrigins=List.of("https://kreiscraft.de");if(sessionCookie==null)sessionCookie=new SessionCookie(null,null);if(heartbeatInterval==null)heartbeatInterval=Duration.ofSeconds(20);if(asyncTimeout==null)asyncTimeout=Duration.ofSeconds(10);if(metricsRefreshInterval==null)metricsRefreshInterval=Duration.ofSeconds(1);if(verificationTimeout==null)verificationTimeout=Duration.ofMinutes(2);if(adminApiKey==null)adminApiKey="";}

@ConfigSerializable public record SessionCookie(String name,Duration maxAge){public SessionCookie{if(name==null||name.isBlank())name="website_user_uuid";if(maxAge==null)maxAge=Duration.ofDays(30);}}}
