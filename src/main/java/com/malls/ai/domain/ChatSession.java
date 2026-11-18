package com.malls.ai.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.malls.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 会话。一次「打开客服窗口」对应一条记录，消息挂在会话下。
 *
 * <p>userId 允许为空：未登录游客也能咨询，此时只靠 sessionNo 定位，
 * 后续登录可通过会话号把历史消息迁移到账号下。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("ai_chat_session")
public class ChatSession extends BaseEntity {

    /** 会话编号，全局唯一，前端本地缓存与断线重连都靠它。 */
    private String sessionNo;

    /** 所属用户 ID，游客会话为空。 */
    private Long userId;

    /** 会话标题，一般取首条用户提问的前若干字，用于会话列表展示。 */
    private String title;

    /** 会话状态：AI 接待 / 转人工 / 已关闭。 */
    private SessionStatus status;

    /** 会话是否仍在服务中：AI 接待或人工接待都不算结束。 */
    public boolean open() {
        return status == SessionStatus.AI || status == SessionStatus.HUMAN;
    }

    /** 会话是否已关闭，关闭后不再接受新消息。 */
    public boolean closed() {
        return status == SessionStatus.CLOSED;
    }

    /** 转人工：已关闭的会话不接收转接，返回 false 由调用方提示重新发起。 */
    public boolean transferToHuman() {
        if (status == SessionStatus.CLOSED) {
            return false;
        }
        this.status = SessionStatus.HUMAN;
        return true;
    }
}
