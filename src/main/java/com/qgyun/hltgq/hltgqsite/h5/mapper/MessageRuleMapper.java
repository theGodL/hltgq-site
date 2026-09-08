package com.qgyun.hltgq.hltgqsite.h5.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.h5.entity.MessageRule;

/**
 * H5 消息中心：消息接收规则表 Mapper（查询走 MP 内置方法，规则量小全量载入内存匹配）。
 */
public interface MessageRuleMapper extends BaseMapper<MessageRule> {
}
