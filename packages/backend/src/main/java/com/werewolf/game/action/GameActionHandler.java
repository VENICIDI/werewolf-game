package com.werewolf.game.action;

import java.util.Map;

/**
 * 游戏行动处理器接口
 * 所有游戏行动（击杀/查验/救人/毒人/守护/投票/开枪/跳过）均实现此接口，
 * 通过 action 字段路由到对应实现。
 */
public interface GameActionHandler {

    /**
     * 处理器对应的行动类型标识，与请求中的 action 字段一一对应
     */
    String getAction();

    /**
     * 校验当前是否允许执行该行动
     *
     * @param context 行动上下文
     * @throws RuntimeException 如果校验不通过
     */
    void validate(ActionContext context);

    /**
     * 执行行动
     *
     * @param context 行动上下文
     * @return 执行结果
     */
    Map<String, Object> execute(ActionContext context);
}
