package com.bbtc.bluebird.modules.note.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段级可编辑性（详情页专用，后端为唯一事实来源）。
 *
 * <p>写路径校验一一对应，前端据此禁用编辑区。
 * <ul>
 *   <li>{@code content}：owner / ADMIN 为 true</li>
 * </ul>
 * 只读可见者（上级视角）全 false。日志日期与标签已下线，不再有对应位。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteFieldPermissionsVO {

    private boolean content;

    public static NoteFieldPermissionsVO none() {
        return new NoteFieldPermissionsVO(false);
    }

    public static NoteFieldPermissionsVO all() {
        return new NoteFieldPermissionsVO(true);
    }
}
