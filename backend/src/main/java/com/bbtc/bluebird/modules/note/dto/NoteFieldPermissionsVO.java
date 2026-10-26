package com.bbtc.bluebird.modules.note.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段级可编辑性（详情页专用，后端为唯一事实来源）。
 *
 * <p>与写路径的校验一一对应，前端据此禁用控件，避免「点了才报 50013」：
 * <ul>
 *   <li>{@code content} / {@code logDate}：owner、ADMIN、未归档的 EDITOR 为 true；归档日志对所有人为 false（50013 已归档只读）</li>
 *   <li>{@code visibility} / {@code tagIds}：仅 owner / ADMIN；EDITOR 修改会 50013</li>
 * </ul>
 * 只读可见者（VIEWER / INTERNAL / PUBLIC / 下属）四个字段全 false。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteFieldPermissionsVO {

    private boolean content;
    private boolean logDate;
    private boolean visibility;
    private boolean tagIds;

    public static NoteFieldPermissionsVO none() {
        return new NoteFieldPermissionsVO(false, false, false, false);
    }

    public static NoteFieldPermissionsVO all() {
        return new NoteFieldPermissionsVO(true, true, true, true);
    }
}
