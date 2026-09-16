package com.lnf.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 隐藏特征（防冒领），对应 item_features 表
 */
@Data
@TableName("item_features")
public class ItemFeature {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    /** 特征问题，如"卡内姓名" */
    private String featureKey;

    /** 答案密文（AES 加密存储，不明文入库） */
    private String answerEncrypted;

    private OffsetDateTime createdAt;
}
