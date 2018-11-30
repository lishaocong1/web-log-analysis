package com.l000phone.bean.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * session的明细数据实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionDetail {
    private int task_id;
    private int user_id;
    private String session_id;
    private int page_id;
    private String action_time;
    private String search_keyword;
    private int click_category_id;
    private int click_product_id;
    private String order_category_ids;
    private String order_product_ids;
    private String pay_category_ids;
    private String pay_product_ids;
}
