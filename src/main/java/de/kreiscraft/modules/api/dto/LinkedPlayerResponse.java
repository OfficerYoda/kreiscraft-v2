package de.kreiscraft.modules.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)public record LinkedPlayerResponse(@JsonProperty("status")String status, // "not_linked" | "pending" | "declined" | "accepted"
@JsonProperty("playerData")PlayerData playerData){

public static LinkedPlayerResponse notLinked(){return new LinkedPlayerResponse("not_linked",null);}

public static LinkedPlayerResponse pending(){return new LinkedPlayerResponse("pending",null);}

public static LinkedPlayerResponse declined(){return new LinkedPlayerResponse("declined",null);}

public static LinkedPlayerResponse acceptedOffline(){return new LinkedPlayerResponse("accepted",null);}

public static LinkedPlayerResponse accepted(PlayerData data){return new LinkedPlayerResponse("accepted",data);}}
