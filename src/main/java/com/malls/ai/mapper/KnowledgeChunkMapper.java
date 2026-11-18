package com.malls.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.malls.ai.domain.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;

/** 知识库片段持久层。 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {
}
