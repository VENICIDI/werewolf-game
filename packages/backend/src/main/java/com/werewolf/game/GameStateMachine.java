package com.werewolf.game;

import com.werewolf.entity.Game;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 游戏状态机 - 管理游戏状态流转
 */
@Component
@Slf4j
public class GameStateMachine {
    
    // 状态处理器注册表
    private final Map<String, StateHandler> stateHandlers = new HashMap<>();
    
    // 状态流转规则
    private final Map<String, Map<String, Transition>> transitions = new HashMap<>();
    
    public GameStateMachine() {
        initTransitions();
    }
    
    /**
     * 初始化状态流转规则
     */
    private void initTransitions() {
        // waiting -> starting
        addTransition("waiting", "starting", 
            ctx -> ctx.getGame().getCurrentPlayers() >= 6,
            ctx -> log.info("游戏开始，玩家数: {}", ctx.getGame().getCurrentPlayers()));
        
        // starting -> night
        addTransition("starting", "night",
            ctx -> true,
            ctx -> {
                ctx.getGame().setStatus(Game.GameStatus.RUNNING);
                ctx.getGame().setCurrentRound(1);
                log.info("进入第1夜");
            });
        
        // night -> day
        addTransition("night", "day",
            ctx -> ctx.getGame().getCurrentPhase() == Game.GamePhase.DAY_START,
            ctx -> log.info("天亮了"));
        
        // day -> checking
        addTransition("day", "checking",
            ctx -> ctx.getGame().getCurrentPhase() == Game.GamePhase.EXECUTION,
            ctx -> log.info("检查胜负条件"));
        
        // checking -> night (继续游戏)
        addTransition("checking", "night",
            ctx -> ctx.getGame().getWinner() == Game.Winner.NONE,
            ctx -> {
                ctx.getGame().setCurrentRound(ctx.getGame().getCurrentRound() + 1);
                log.info("进入第{}夜", ctx.getGame().getCurrentRound());
            });
        
        // checking -> finished (游戏结束)
        addTransition("checking", "finished",
            ctx -> ctx.getGame().getWinner() != Game.Winner.NONE,
            ctx -> {
                ctx.getGame().setStatus(Game.GameStatus.FINISHED);
                log.info("游戏结束，获胜方: {}", ctx.getGame().getWinner());
            });
        
        // finished -> waiting (重新开始)
        addTransition("finished", "waiting",
            ctx -> true,
            ctx -> {
                ctx.getGame().setStatus(Game.GameStatus.PREPARING);
                ctx.getGame().setCurrentRound(0);
                ctx.getGame().setWinner(Game.Winner.NONE);
                log.info("游戏重置");
            });
    }
    
    /**
     * 添加状态流转规则
     */
    public void addTransition(String from, String to, 
                             java.util.function.Predicate<Context> condition,
                             Consumer<Context> action) {
        transitions.computeIfAbsent(from, k -> new HashMap<>())
                   .put(to, new Transition(to, condition, action));
    }
    
    /**
     * 尝试状态流转
     */
    public boolean tryTransition(Context ctx, String targetState) {
        String currentState = getStateName(ctx.getGame().getStatus());
        
        Map<String, Transition> available = transitions.get(currentState);
        if (available == null) {
            log.warn("当前状态 {} 没有可流转的规则", currentState);
            return false;
        }
        
        Transition transition = available.get(targetState);
        if (transition == null) {
            log.warn("无法从 {} 流转到 {}", currentState, targetState);
            return false;
        }
        
        // 检查条件
        if (!transition.getCondition().test(ctx)) {
            log.warn("状态流转条件不满足: {} -> {}", currentState, targetState);
            return false;
        }
        
        // 执行流转
        log.info("状态流转: {} -> {}", currentState, targetState);
        transition.getAction().accept(ctx);
        
        return true;
    }
    
    /**
     * 获取当前状态名称
     */
    private String getStateName(Game.GameStatus status) {
        return switch (status) {
            case PREPARING -> "waiting";
            case RUNNING -> {
                // 根据阶段判断
                yield "running";
            }
            case PAUSED -> "paused";
            case FINISHED -> "finished";
        };
    }
    
    /**
     * 获取可用流转目标
     */
    public java.util.Set<String> getAvailableTransitions(Game.GameStatus status) {
        String stateName = getStateName(status);
        Map<String, Transition> available = transitions.get(stateName);
        return available != null ? available.keySet() : java.util.Set.of();
    }
    
    /**
     * 注册状态处理器
     */
    public void registerHandler(String state, StateHandler handler) {
        stateHandlers.put(state, handler);
    }
    
    /**
     * 执行状态处理
     */
    public void handleState(Context ctx) {
        String stateName = getStateName(ctx.getGame().getStatus());
        StateHandler handler = stateHandlers.get(stateName);
        if (handler != null) {
            handler.handle(ctx);
        }
    }
    
    /**
     * 状态流转规则
     */
    @Data
    public static class Transition {
        private final String targetState;
        private final java.util.function.Predicate<Context> condition;
        private final Consumer<Context> action;
    }
    
    /**
     * 状态处理器接口
     */
    public interface StateHandler {
        void handle(Context ctx);
    }
    
    /**
     * 上下文
     */
    @Data
    public static class Context {
        private Game game;
        private Object data;
    }
}
