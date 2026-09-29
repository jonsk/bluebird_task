package com.bbtc.bluebird.modules.task.dto;

import java.time.Instant;

/** 自定义栏（含条目，GET /menus）。 */
public class MenuDTO {
    public Long id;
    public Long userId;
    public String name;
    public Integer sort;
    public Instant createdAt;
    public java.util.List<MenuItemDTO> items = new java.util.ArrayList<>();

    public MenuDTO() {
    }

    public MenuDTO(Long id, Long userId, String name, Integer sort, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.sort = sort;
        this.createdAt = createdAt;
    }
}
