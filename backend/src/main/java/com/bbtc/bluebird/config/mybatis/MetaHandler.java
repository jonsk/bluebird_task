package com.bbtc.bluebird.config.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.bbtc.bluebird.common.model.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 审计字段自动填充（02 §1.6）。createdBy/updatedBy 取当前用户；更新时为空则保留原值。
 */
@Component
public class MetaHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now();
        Long uid = UserContext.currentUserId();
        strictInsertFill(metaObject, "createdAt", Instant.class, now);
        strictInsertFill(metaObject, "updatedAt", Instant.class, now);
        if (uid != null) {
            strictInsertFill(metaObject, "createdBy", Long.class, uid);
            strictInsertFill(metaObject, "updatedBy", Long.class, uid);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
        Long uid = UserContext.currentUserId();
        if (uid != null) {
            strictUpdateFill(metaObject, "updatedBy", Long.class, uid);
        }
    }
}
