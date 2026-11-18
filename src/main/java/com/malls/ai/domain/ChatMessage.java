package com.malls.ai.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.malls.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 会话消息。只追加不修改，历史上下文按 create_time 升序拼装提示词。
 *
 * <p>citations 存本次回答引用的知识片段 JSON，用于前端「查看依据」，
 * latencyMs / tokenCount 是可观测性指标，为后续调优留数据。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("ai_chat_message")
public class ChatMessage extends BaseEntity {

    /** 所属会话 ID，逻辑外键指向 ai_chat_session。 */
    private Long sessionId;

    /** 发言方：用户 / AI / 系统。 */
    private ChatRole role;

    /** 消息正文；AI 消息为模型输出，系统消息为超时、转人工等提示。 */
    private String content;

    /** 引用的知识片段 JSON 数组，仅 AI 回答可能有值。 */
    private String citations;

    /** 生成耗时（毫秒），用户消息为 0。 */
    private int latencyMs;

    /** 消耗 token 数，用于成本统计与上下文裁剪。 */
    private int tokenCount;

    /** 是否为用户发的消息。 */
    public boolean fromUser() {
        return role == ChatRole.USER;
    }

    /** 是否为 AI 回答，只有这类消息可被评价。 */
    public boolean fromAssistant() {
        return role == ChatRole.ASSISTANT;
    }

    /** 是否带有知识引用，前端据此渲染「参考来源」。 */
    public boolean hasCitations() {
        return citations != null && !citations.isBlank();
    }
}
