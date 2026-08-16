DROP TABLE IF EXISTS `flash_sale_setmeal_activity`;
CREATE TABLE `flash_sale_setmeal_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setmeal_id` bigint NOT NULL COMMENT '套餐id',
  `activity_name` varchar(64) COLLATE utf8_bin NOT NULL COMMENT '活动名称',
  `setmeal_name` varchar(64) COLLATE utf8_bin NOT NULL COMMENT '套餐名称快照',
  `setmeal_price` decimal(10,2) NOT NULL COMMENT '套餐价格快照',
  `setmeal_image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '套餐图片快照',
  `stock` int NOT NULL COMMENT '活动库存',
  `status` int NOT NULL DEFAULT '0' COMMENT '活动状态 0禁用 1启用',
  `start_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  KEY `idx_flash_sale_setmeal_activity_status_time` (`status`,`start_time`,`end_time`),
  KEY `idx_flash_sale_setmeal_activity_setmeal_id` (`setmeal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='限量套餐活动表';

DROP TABLE IF EXISTS `flash_sale_setmeal_order`;
CREATE TABLE `flash_sale_setmeal_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint NOT NULL COMMENT '活动id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `setmeal_id` bigint NOT NULL COMMENT '套餐id',
  `order_no` varchar(64) COLLATE utf8_bin NOT NULL COMMENT '抢购单号',
  `activity_name` varchar(64) COLLATE utf8_bin NOT NULL COMMENT '活动名称快照',
  `setmeal_name` varchar(64) COLLATE utf8_bin NOT NULL COMMENT '套餐名称快照',
  `setmeal_price` decimal(10,2) NOT NULL COMMENT '套餐价格快照',
  `setmeal_image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '套餐图片快照',
  `status` int NOT NULL DEFAULT '1' COMMENT '抢购状态 1成功',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flash_sale_activity_user` (`activity_id`,`user_id`),
  UNIQUE KEY `uk_flash_sale_order_no` (`order_no`),
  KEY `idx_flash_sale_order_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='限量套餐抢购记录表';
