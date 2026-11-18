package com.malls.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.malls.ai.domain.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** AI 会话消息持久层。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
