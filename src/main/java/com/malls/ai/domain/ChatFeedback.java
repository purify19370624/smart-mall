package com.malls.ai.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.malls.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 回答满意度反馈。用户对单条 AI 消息点赞 / 点踩，可附文字说明。
 *
 * <p>score 只有 1（满意）/ 0（不满意）两档，保持轻量；
 * 需要细粒度评分时另开字段，不改这里的语义。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("ai_chat_feedback")
public class ChatFeedback extends BaseEntity {

    /** 被评价的 AI 消息 ID，逻辑外键指向 ai_chat_message。 */
    private Long messageId;

    /** 所属会话 ID，便于按会话聚合满意度。 */
    private Long sessionId;

    /** 评价人 ID，游客为空。 */
    private Long userId;

    /** 评分：1 满意，0 不满意。 */
    private int score;

    /** 文字反馈，点踩时通常用来收集具体问题。 */
    private String commentText;

    /** 是否为正面评价。 */
    public boolean satisfied() {
        return score == 1;
    }

    /** 是否为负面评价。 */
    public boolean unsatisfied() {
        return score == 0;
    }

    /** 是否附带文字说明。 */
    public boolean hasComment() {
        return commentText != null && !commentText.isBlank();
    }
}
