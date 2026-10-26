package com.bbtc.bluebird.modules.note.util;

import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.modules.org.application.DepartmentPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 日志可见性 SQL 构建器 —— {@code scope=visible} 与 {@code NoteAuthService.isVisible} 的**同源载体**。
 *
 * <p>语义：{@code visible = isVisible}（owner / 已授权 / 下属，受开关约束）。
 * 对 ADMIN 恒为 {@code 1=1}（与 {@code scope=all} 结果集相同，仅语义/入口不同）。
 *
 * <p>生成的 SQL 只拼接可信的长整型用户 id，不接受任何用户字符串，无注入面。
 */
@Component
@RequiredArgsConstructor
public class NoteVisibilitySqlBuilder {

    private static final Set<String> SCOPES = Set.of(
            "mine", "shared", "subordinate", "visible", "all");

    private final DepartmentPolicy departmentPolicy;

    @Value("${app.security.manager-can-read-subordinate:false}")
    private boolean managerCanReadSubordinate;

    public boolean managerCanReadSubordinate() {
        return managerCanReadSubordinate;
    }

    /** scope 是否合法。 */
    public boolean validScope(String scope) {
        return scope != null && SCOPES.contains(scope);
    }

    /**
     * scope 对应的 base 条件（不含 archived / visibility 附加过滤）。
     *
     * @throws HttpStatusException 403：{@code scope=all} 非 ADMIN；{@code scope=subordinate} 开关关闭
     */
    public String scopePredicate(String scope, Long me, boolean admin) {
        return switch (scope) {
            case "mine" -> "note.owner_id = " + me;
            case "shared" -> "note.owner_id IN (SELECT g.grantor_id FROM note_user_grant g"
                    + " WHERE g.grantee_id = " + me + " AND g.status = 'APPROVED')";
            case "subordinate" -> {
                if (!managerCanReadSubordinate) {
                    throw new HttpStatusException(HttpStatus.FORBIDDEN,
                            com.bbtc.bluebird.common.exception.ErrorCode.NOTE_SCOPE_DISABLED);
                }
                yield "note.owner_id IN " + departmentPolicy.subordinateOwnerSql(me);
            }
            case "visible" -> visiblePredicate(me, admin);
            case "all" -> {
                if (!admin) {
                    throw HttpStatusException.forbidden();
                }
                yield "1 = 1";
            }
            default -> throw HttpStatusException.badRequest("非法 scope");
        };
    }

    /**
     * 与 {@code NoteAuthService.isVisible} 逐分支等价的可见性条件。
     *
     * <p>下属分支必须带 {@code managerCanReadSubordinate} 条件。
     */
    public String visiblePredicate(Long me, boolean admin) {
        if (admin) {
            return "1 = 1";
        }
        StringBuilder sb = new StringBuilder("(");
        sb.append("note.owner_id = ").append(me);
        sb.append(" OR EXISTS (SELECT 1 FROM note_user_grant g")
                .append(" WHERE g.grantor_id = note.owner_id AND g.grantee_id = ").append(me)
                .append(" AND g.status = 'APPROVED')");
        if (managerCanReadSubordinate) {
            sb.append(" OR note.owner_id IN ").append(departmentPolicy.subordinateOwnerSql(me));
        }
        sb.append(")");
        return sb.toString();
    }

    /** 子查询：scope 命中的 note id（供标签 facet / 日历等聚合使用，调用方自行追加 deleted）。 */
    public String noteIdSubquery(String scope, Long me, boolean admin) {
        return "SELECT note.id FROM note WHERE " + scopePredicate(scope, me, admin);
    }
}
