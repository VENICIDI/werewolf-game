package com.werewolf.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 游戏配置类 - 映射 game-flow.json
 */
@Data
public class GameConfig {
    
    private String version;
    private String description;
    
    @JsonProperty("gameModes")
    private Map<String, GameMode> gameModes;
    
    @Data
    public static class GameMode {
        private String name;
        private int playerCount;
        
        @JsonProperty("roleDistribution")
        private Map<String, Integer> roleDistribution;
        
        @JsonProperty("flow")
        private Flow flow;
        
        @JsonProperty("rules")
        private GameRules rules;
    }
    
    @Data
    public static class Flow {
        @JsonProperty("night")
        private List<PhaseConfig> night;
        
        @JsonProperty("day")
        private List<PhaseConfig> day;
    }
    
    @Data
    public static class PhaseConfig {
        private String phase;
        private int duration;
        private String broadcast;
        
        @JsonProperty("endBroadcast")
        private String endBroadcast;
        
        @JsonProperty("roles")
        private List<String> roles;
        
        @JsonProperty("action")
        private String action;
        
        @JsonProperty("showDeathInfo")
        private Boolean showDeathInfo;
        
        @JsonProperty("allowLastWords")
        private Boolean allowLastWords;
        
        @JsonProperty("lastWordsTime")
        private Integer lastWordsTime;
        
        @JsonProperty("speakOrder")
        private String speakOrder;
        
        @JsonProperty("speakTime")
        private Integer speakTime;
        
        @JsonProperty("canAbstain")
        private Boolean canAbstain;
    }
    
    @Data
    public static class GameRules {
        @JsonProperty("firstNightNoKill")
        private Boolean firstNightNoKill;
        
        @JsonProperty("witchCanSaveSelf")
        private Boolean witchCanSaveSelf;
        
        @JsonProperty("witchCanSaveFirstNight")
        private Boolean witchCanSaveFirstNight;
        
        @JsonProperty("guardCanProtectSelf")
        private Boolean guardCanProtectSelf;
        
        @JsonProperty("guardCannotProtectConsecutive")
        private Boolean guardCannotProtectConsecutive;
        
        @JsonProperty("guardWitchConflict")
        private String guardWitchConflict;
        
        @JsonProperty("hunterCanShootWhenDied")
        private Boolean hunterCanShootWhenDied;
        
        @JsonProperty("hunterCanShootWhenVotedOut")
        private Boolean hunterCanShootWhenVotedOut;
        
        @JsonProperty("hunterCannotShootWhenPoisoned")
        private Boolean hunterCannotShootWhenPoisoned;
        
        @JsonProperty("seerKnowsIdentity")
        private Boolean seerKnowsIdentity;
        
        @JsonProperty("tieVote")
        private String tieVote;
        
        @JsonProperty("consecutiveNoKill")
        private Boolean consecutiveNoKill;
    }
}
