package com.malls.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.malls.ai.domain.ChatFeedback;
import org.apache.ibatis.annotations.Mapper;

/** AI 回答满意度持久层。 */
@Mapper
public interface ChatFeedbackMapper extends BaseMapper<ChatFeedback> {
}
