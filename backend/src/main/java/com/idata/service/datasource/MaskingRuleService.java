package com.idata.service.datasource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idata.dto.MaskingRuleRequest;
import com.idata.dto.MaskingRuleVO;
import com.idata.entity.MaskingRule;
import com.idata.mapper.MaskingRuleMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MaskingRuleService {

    private final MaskingRuleMapper maskingRuleMapper;

    public MaskingRuleService(MaskingRuleMapper maskingRuleMapper) {
        this.maskingRuleMapper = maskingRuleMapper;
    }

    public List<MaskingRuleVO> listAll() {
        return maskingRuleMapper.selectList(
                new LambdaQueryWrapper<MaskingRule>()
                        .orderByDesc(MaskingRule::getUpdatedAt)
                )
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public MaskingRuleVO getById(Long id) {
        MaskingRule rule = maskingRuleMapper.selectById(id);
        if (rule == null) {
            throw new IllegalArgumentException("脱敏规则不存在: " + id);
        }
        return toVO(rule);
    }

    public MaskingRuleVO create(MaskingRuleRequest req) {
        MaskingRule rule = new MaskingRule();
        rule.setName(req.getName());
        rule.setType(req.getType());
        rule.setConfig(req.getConfig());
        rule.setDescription(req.getDescription());
        maskingRuleMapper.insert(rule);
        return toVO(rule);
    }

    public MaskingRuleVO update(MaskingRuleRequest req) {
        MaskingRule rule = maskingRuleMapper.selectById(req.getId());
        if (rule == null) {
            throw new IllegalArgumentException("脱敏规则不存在: " + req.getId());
        }
        rule.setName(req.getName());
        rule.setType(req.getType());
        rule.setConfig(req.getConfig());
        rule.setDescription(req.getDescription());
        maskingRuleMapper.updateById(rule);
        return toVO(maskingRuleMapper.selectById(req.getId()));
    }

    public void delete(Long id) {
        if (maskingRuleMapper.selectById(id) == null) {
            throw new IllegalArgumentException("脱敏规则不存在: " + id);
        }
        maskingRuleMapper.deleteById(id);
    }

    private MaskingRuleVO toVO(MaskingRule rule) {
        MaskingRuleVO vo = new MaskingRuleVO();
        vo.setId(rule.getId());
        vo.setName(rule.getName());
        vo.setType(rule.getType());
        vo.setConfig(rule.getConfig());
        vo.setDescription(rule.getDescription());
        vo.setCreatedAt(rule.getCreatedAt());
        vo.setUpdatedAt(rule.getUpdatedAt());
        return vo;
    }
}
