# 毕业设计论文 · 核心技术章节索引

> 项目：基于大语言模型的 AI 狼人杀游戏系统
> 作者：veennyyang

本目录包含论文核心技术章节的完整文档，每篇都附有多种 UML 图（类图、时序图、状态图、流程图、组件图、ER 图）。

## 章节导航

| 编号 | 文档 | 主题 | 核心 UML |
|------|------|------|---------|
| 01 | [Prompt 工程](./01-prompt-engineering.md) | 角色化提示词的分层设计 | 五层洋葱模型图 / 角色策略类图 / 模板注入流程图 / Persona 时序图 |
| 02 | [Agent 记忆系统](./02-agent-memory.md) | 三层记忆架构 | 记忆架构图 / 记忆系统类图 / 记忆流转时序图 / 遗忘算法流程图 / 事件枚举类图 |
| 03 | [RAG 工程](./03-rag-engineering.md) | 知识库构建与检索调优 | 整体架构图 / 分块流程图 / MMR 流程图 / 检索时序图 / RAG 类图 / 状态图 |
| 04 | [游戏状态机](./04-game-state-machine.md) | 狼人杀流程建模 | 宏观状态图 / 微观阶段状态图 / 状态机类图 / 转换时序图 / 胜负判定流程 / ER 图 |
| 05 | [前后端通信与一致性](./05-frontend-backend-sync.md) | 状态同步与容错 | 通信架构图 / 消息分类图 / 同步时序图 / 推送路由流程图 / 心跳状态图 / 投票端到端时序 |

## 论文推荐章节结构

基于这 5 篇文档，可以构建论文的第 3~7 章：

```
第1章 绪论（研究背景+目的+结构）
第2章 相关技术（LLM/RAG/FSM 综述）
第3章 系统需求与总体设计
第4章 AI Agent 核心技术
  4.1 Prompt 工程 → 见 01-prompt-engineering.md
  4.2 三层记忆系统 → 见 02-agent-memory.md
  4.3 RAG 知识库 → 见 03-rag-engineering.md
第5章 游戏引擎设计与实现
  5.1 状态机建模 → 见 04-game-state-machine.md
  5.2 行动分发与定时调度
第6章 前后端通信与状态一致性 → 见 05-frontend-backend-sync.md
第7章 系统测试与评估
第8章 总结与展望
```

## UML 图总览

所有 UML 使用 Mermaid 语法嵌入，GitHub/VSCode/Typora 均可直接渲染。若需导出为 PNG/SVG 供论文排版：

```bash
# 安装 mermaid-cli
npm install -g @mermaid-js/mermaid-cli

# 从 markdown 批量导出
mmdc -i 01-prompt-engineering.md -o assets/ch01.png
```

## 引用与数据

文档中涉及的实测数据（如 Recall@k、破设率等）来源于本项目 `packages/ai-service/tests/` 下的自动化测试与 `logs/` 下的游戏日志统计。正式写入论文时建议：
1. 固定数据采集的时间点与版本号
2. 截图保留原始日志
3. 所有图表标注数据来源
