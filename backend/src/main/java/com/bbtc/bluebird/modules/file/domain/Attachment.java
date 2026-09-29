package com.bbtc.bluebird.modules.file.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 附件（02 §5.3）。物理删除，无 deleted 列。 */
@Data
@TableName("attachment")
public class Attachment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;
    private String fileName;
    private String relativePath;
    private Long size;
    private String md5;
    private Long createdBy;
    private Instant createdAt;
}
