package com.bbtc.bluebird.modules.task.dto;

import java.util.ArrayList;
import java.util.List;

/** 分类树节点（GET /categories）。 */
public class CategoryNodeDTO {
    public Long id;
    public String name;
    public Long parentId;
    public String scope;
    public Long deptId;
    public Long ownerId;
    public Integer sort;
    public int taskCount;
    public java.time.Instant createdAt;
    public java.time.Instant updatedAt;
    public List<CategoryNodeDTO> children = new ArrayList<>();

    public CategoryNodeDTO() {
    }

    public CategoryNodeDTO(Long id, String name, Long parentId, String scope, Long deptId, Long ownerId,
                           Integer sort, java.time.Instant createdAt, java.time.Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.scope = scope;
        this.deptId = deptId;
        this.ownerId = ownerId;
        this.sort = sort;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
