package com.malls.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.malls.ai.domain.KnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;

/** 知识库文档持久层。 */
@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {
}
