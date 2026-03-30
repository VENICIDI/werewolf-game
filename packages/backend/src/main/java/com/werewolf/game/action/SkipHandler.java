package com.werewolf.game.action;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SkipHandler implements GameActionHandler {

    @Override
    public String getAction() {
        return "skip";
    }

    @Override
    public void validate(ActionContext context) {
        // skip 无需特殊校验
    }

    @Override
    public Map<String, Object> execute(ActionContext context) {
        log.info("{}号 跳过行动", context.getPlayer().getSeatNumber());

        Map<String, Object> result = new HashMap<>();
        result.put("message", "跳过行动");
        return result;
    }
}
