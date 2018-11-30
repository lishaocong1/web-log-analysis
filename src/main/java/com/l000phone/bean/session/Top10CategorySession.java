package com.l000phone.bean.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 存储top10每个品类的点击top10的session的实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Top10CategorySession {
    private int task_id;
    private int  category_id;
    private String session_id;
    private int click_count;
}
