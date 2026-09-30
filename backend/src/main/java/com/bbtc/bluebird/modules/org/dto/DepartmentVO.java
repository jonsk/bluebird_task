package com.bbtc.bluebird.modules.org.dto;

import java.util.ArrayList;
import java.util.List;

/** 部门树节点（GET /departments，02 §3.4）。 */
public class DepartmentVO {
    public Long id;
    public String name;
    public Long parentId;
    public Long leaderId;
    public Integer sort;
    /** 系统默认顶级部门：可改名，不可删除（用户反馈 #5）。 */
    public Boolean system;
    public List<DepartmentVO> children = new ArrayList<>();

    public DepartmentVO() {
    }

    public DepartmentVO(Long id, String name, Long parentId, Long leaderId, Integer sort) {
        this(id, name, parentId, leaderId, sort, Boolean.FALSE);
    }

    public DepartmentVO(Long id, String name, Long parentId, Long leaderId, Integer sort, Boolean system) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.leaderId = leaderId;
        this.sort = sort;
        this.system = system;
    }
}
