package com.l000phone.bean.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封装特定品类点击,下单,和支付总数实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Top10Category {
    private int task_id;
    private int category_id;
    private int click_count;
    private int order_count;
    private int pay_count;
}
