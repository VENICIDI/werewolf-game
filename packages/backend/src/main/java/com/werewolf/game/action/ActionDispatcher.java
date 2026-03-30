package com.werewolf.game.action;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 行动分发器
 * 注册所有 GameActionHandler 实现，根据 action 字段路由到对应处理器。
 * 扩展新行动只需新增一个 GameActionHandler 实现类并在此注册即可。
 */
@Component
@Slf4j
public class ActionDispatcher {

    private final Map<String, GameActionHandler> handlerRegistry = new HashMap<>();

    @PostConstruct
    public void init() {
        register(new WerewolfKillHandler());
        register(new SeerCheckHandler());
        register(new WitchSaveHandler());
        register(new WitchPoisonHandler());
        register(new GuardProtectHandler());
        register(new VoteHandler());
        register(new HunterShootHandler());
        register(new SkipHandler());

        log.info("行动分发器初始化完成，已注册 {} 种处理器: {}",
                handlerRegistry.size(), handlerRegistry.keySet());
    }

    /**
     * 注册处理器
     */
    public void register(GameActionHandler handler) {
        handlerRegistry.put(handler.getAction(), handler);
    }

    /**
     * 根据 action 获取对应处理器
     */
    public GameActionHandler getHandler(String action) {
        GameActionHandler handler = handlerRegistry.get(action);
        if (handler == null) {
            throw new RuntimeException("未知的行动类型: " + action
                    + "，支持: " + handlerRegistry.keySet());
        }
        return handler;
    }

    /**
     * 校验 + 执行（一步调用）
     */
    public Map<String, Object> dispatch(String action, ActionContext context) {
        GameActionHandler handler = getHandler(action);
        handler.validate(context);
        return handler.execute(context);
    }

    /**
     * 检查某行动是否已注册
     */
    public boolean isSupported(String action) {
        return handlerRegistry.containsKey(action);
    }
}
