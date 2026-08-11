package com.mac.portfolio.enterprise.model;

import java.time.Instant;
import java.util.Map;

/**
 * 一份待入库的企业文档，是 HTTP JSON 与内部入库服务之间的统一数据模型。
 *
 * @param externalId 来源系统中的文档唯一标识，与 source 一起用于去重
 * @param source 来源系统，例如 github、notion、manual
 * @param sourceType 文档类型，例如 policy、ticket、text
 * @param title 文档标题，也会作为无 Markdown 标题时的默认章节路径
 * @param content 已提取成字符串的正文；这里不直接接收 PDF/JPG 二进制
 * @param tenantId 租户标识，用于查询时的数据隔离
 * @param department 所属部门，用于查询时的 ACL 过滤
 * @param accessLevel 文档访问级别
 * @param metadata 来源系统附带的扩展元数据
 * @param sourceUpdatedAt 来源文档最后更新时间
 */
public record EnterpriseDocumentInput(
        String externalId,
        String source,
        String sourceType,
        String title,
        String content,
        String tenantId,
        String department,
        String accessLevel,
        Map<String, Object> metadata,
        Instant sourceUpdatedAt) {

    public EnterpriseDocumentInput {
        // 去重所需的三个来源字段不能为空；require 同时会去掉首尾空格。
        externalId = require(externalId, "externalId");
        source = require(source, "source");
        sourceType = require(sourceType, "sourceType");

        // 为可选字段设置安全默认值，让后续切块和 ACL 代码不必重复处理 null。
        title = title == null ? "" : title.trim();
        content = content == null ? "" : content;
        tenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim();
        department = department == null || department.isBlank() ? "engineering" : department.trim().toLowerCase();
        accessLevel = accessLevel == null || accessLevel.isBlank() ? "public" : accessLevel.trim().toLowerCase();

        // 复制为不可变 Map，避免调用方在入库过程中偷偷修改元数据。
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String require(String value, String field) {
        // 尽早拒绝无法建立稳定文档身份的数据。
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
