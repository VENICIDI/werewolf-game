package com.werewolf.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 角色配置类 - 映射 roles-complete.json
 */
@Data
public class RoleConfig {
    
    private String version;
    private String description;
    
    @JsonProperty("roles")
    private Map<String, Role> roles;
    
    @JsonProperty("camps")
    private Map<String, Camp> camps;
    
    @JsonProperty("roleTypes")
    private Map<String, RoleType> roleTypes;
    
    @Data
    public static class Role {
        private String id;
        private String name;
        
        @JsonProperty("nameEn")
        private String nameEn;
        
        private String camp;
        
        @JsonProperty("campName")
        private String campName;
        
        private String type;
        private String description;
        
        @JsonProperty("shortDesc")
        private String shortDesc;
        
        private List<Skill> skills;
        
        @JsonProperty("actionPhase")
        private String actionPhase;
        
        private Integer priority;
        
        @JsonProperty("canUseSkillAtNight")
        private Boolean canUseSkillAtNight;
        
        @JsonProperty("winCondition")
        private String winCondition;
        
        private Integer difficulty;
        
        @JsonProperty("teamAction")
        private Boolean teamAction;
        
        private List<String> tips;
    }
    
    @Data
    public static class Skill {
        private String id;
        private String name;
        private String description;
        
        @JsonProperty("targetType")
        private String targetType;
        
        @JsonProperty("targetCamp")
        private String targetCamp;
        
        @JsonProperty("canTargetSelf")
        private Boolean canTargetSelf;
        
        @JsonProperty("canTargetTeammate")
        private Boolean canTargetTeammate;
        
        @JsonProperty("usageLimit")
        private Integer usageLimit;
        
        private Integer priority;
        
        @JsonProperty("resultType")
        private String resultType;
        
        @JsonProperty("resultGood")
        private String resultGood;
        
        @JsonProperty("resultBad")
        private String resultBad;
        
        @JsonProperty("trigger")
        private String trigger;
        
        @JsonProperty("canBeBlocked")
        private Boolean canBeBlocked;
        
        @JsonProperty("blockedBy")
        private List<String> blockedBy;
        
        @JsonProperty("cannotGuardConsecutive")
        private Boolean cannotGuardConsecutive;
        
        @JsonProperty("guardWithWitchConflict")
        private Boolean guardWithWitchConflict;
    }
    
    @Data
    public static class Camp {
        private String id;
        private String name;
        
        @JsonProperty("nameEn")
        private String nameEn;
        
        private String description;
        
        @JsonProperty("winCondition")
        private String winCondition;
        
        private List<String> roles;
    }
    
    @Data
    public static class RoleType {
        private String name;
        private String description;
    }
}
