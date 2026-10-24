package com.bbtc.bluebird.modules.note.dto;

/**
 * 授权发起请求。
 *
 * <ul>
 *   <li>OWNER：{@code granteeId + role}，{@code grantorId} 必须为空（服务端强制 grantor=me）；</li>
 *   <li>SUPERIOR：{@code grantorId + role}，{@code granteeId} 必须为空（服务端推导 grantee=me）；</li>
 *   <li>ADMIN：{@code grantorId + granteeId + role}。</li>
 * </ul>
 */
public record NoteUserGrantReq(String mode, Long grantorId, Long granteeId, String role) {
}
