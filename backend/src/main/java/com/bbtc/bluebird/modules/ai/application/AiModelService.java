package com.bbtc.bluebird.modules.ai.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.ai.domain.AiModelConfig;
import com.bbtc.bluebird.modules.ai.dto.AiModelSaveReq;
import com.bbtc.bluebird.modules.ai.dto.AiModelTestResult;
import com.bbtc.bluebird.modules.ai.dto.AiModelVO;
import com.bbtc.bluebird.modules.ai.infrastructure.AiModelConfigMapper;
import com.bbtc.bluebird.modules.config.application.ConfigCrypto;
import com.bbtc.bluebird.common.model.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** AI 模型配置管理：多模型、全局单选启用、密钥加密。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private static final String MASK = "******";
    private static final String PROVIDER = "OPENAI_COMPATIBLE";

    private final AiModelConfigMapper mapper;
    private final ConfigCrypto crypto;
    private final AiClient aiClient;

    public List<AiModelVO> list() {
        return mapper.selectList(Wrappers.<AiModelConfig>lambdaQuery()
                        .orderByDesc(AiModelConfig::getActive)
                        .orderByAsc(AiModelConfig::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public AiModelVO create(AiModelSaveReq req) {
        AiModelConfig entity = new AiModelConfig();
        apply(entity, req, true);
        boolean first = mapper.selectCount(null) == 0;
        entity.setActive(first && isEnabled(entity) ? 1 : 0);
        mapper.insert(entity);
        return toVO(entity);
    }

    public AiModelVO update(Long id, AiModelSaveReq req) {
        AiModelConfig entity = require(id);
        apply(entity, req, false);
        boolean enabled = isEnabled(entity);
        if (!enabled && isActive(entity)) {
            entity.setActive(0);
        }
        mapper.updateById(entity);
        return toVO(entity);
    }

    public void delete(Long id) {
        AiModelConfig entity = require(id);
        if (isActive(entity)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前正在使用的模型不能删除，请先切换其他模型");
        }
        mapper.deleteById(id);
    }

    @Transactional
    public AiModelVO activate(Long id) {
        AiModelConfig target = require(id);
        if (!isEnabled(target)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "停用的模型不能设为当前模型");
        }
        mapper.update(null, Wrappers.<AiModelConfig>lambdaUpdate()
                .eq(AiModelConfig::getActive, 1)
                .set(AiModelConfig::getActive, 0));
        target.setActive(1);
        mapper.updateById(target);
        return toVO(target);
    }

    public AiModelTestResult test(Long id) {
        AiModelConfig entity = require(id);
        log.info("收到 AI 模型测试请求：id={}, name={}, operator={}",
                id, entity.getName(), UserContext.currentUserId());
        AiModelTestResult result = aiClient.test(entity, decryptKey(entity));
        log.info("AI 模型测试返回：id={}, ok={}, latencyMs={}, model={}, message={}",
                id, result.ok(), result.latencyMs(), result.model(), result.message());
        return result;
    }

    public boolean hasActive() {
        return mapper.selectCount(Wrappers.<AiModelConfig>lambdaQuery()
                .eq(AiModelConfig::getActive, 1)
                .eq(AiModelConfig::getEnabled, 1)) > 0;
    }

    public AiModelConfig activeEntity() {
        AiModelConfig entity = mapper.selectOne(Wrappers.<AiModelConfig>lambdaQuery()
                .eq(AiModelConfig::getActive, 1)
                .eq(AiModelConfig::getEnabled, 1)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "未启用 AI 模型，请先在后台配置");
        }
        return entity;
    }

    public String decryptKey(AiModelConfig entity) {
        return crypto.decrypt(entity.getApiKeyCipher());
    }

    private AiModelConfig require(Long id) {
        AiModelConfig entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 模型不存在");
        }
        return entity;
    }

    private void apply(AiModelConfig entity, AiModelSaveReq req, boolean creating) {
        entity.setName(req.name().trim());
        entity.setProvider(StringUtils.hasText(req.provider()) ? req.provider().trim() : PROVIDER);
        entity.setBaseUrl(req.baseUrl().trim());
        entity.setModel(req.model().trim());
        entity.setTemperature(req.temperature() == null ? 0.3 : req.temperature());
        entity.setMaxTokens(req.maxTokens() == null ? 2048 : req.maxTokens());
        entity.setTimeoutSeconds(req.timeoutSeconds() == null ? 60 : req.timeoutSeconds());
        entity.setEnabled(req.enabled() == null || req.enabled() ? 1 : 0);
        String key = req.apiKey();
        if (StringUtils.hasText(key) && !MASK.equals(key.trim())) {
            entity.setApiKeyCipher(crypto.encrypt(key.trim()));
        } else if (creating) {
            entity.setApiKeyCipher(null);
        }
    }

    private boolean isEnabled(AiModelConfig entity) {
        return entity.getEnabled() != null && entity.getEnabled() == 1;
    }

    private boolean isActive(AiModelConfig entity) {
        return entity.getActive() != null && entity.getActive() == 1;
    }

    private AiModelVO toVO(AiModelConfig entity) {
        return new AiModelVO(
                entity.getId(),
                entity.getName(),
                entity.getProvider(),
                entity.getBaseUrl(),
                entity.getModel(),
                entity.getTemperature(),
                entity.getMaxTokens(),
                entity.getTimeoutSeconds(),
                isEnabled(entity),
                isActive(entity),
                StringUtils.hasText(entity.getApiKeyCipher()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
