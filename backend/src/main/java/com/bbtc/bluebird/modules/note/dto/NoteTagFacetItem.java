package com.bbtc.bluebird.modules.note.dto;

/** 标签 facet（scope=mine 含 id；其余 scope 无 id，按 name 过滤）。 */
public record NoteTagFacetItem(Long id, String name, long count) {
}
