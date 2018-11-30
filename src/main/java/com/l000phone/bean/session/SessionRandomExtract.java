package com.l000phone.bean.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按时间比例随机抽取出来的session实体类
 */
@Data
@NoArgsConstructor
public class SessionRandomExtract {
    /**
     * 任务编号
     */
    private int task_id;
    /**
     * session id
     */
    private String session_id;
    /**
     * session开始时间
     */
    private String start_time;

    /**
     * session结束时间
     */
    private String end_time;

    /**
     * 检索的所有关键字
     */
    private String search_keywords;

    public SessionRandomExtract(int task_id, String session_id, String start_time, String end_time, String search_keywords) {
        this.task_id = task_id;
        this.session_id = session_id;
        this.start_time = start_time;
        this.end_time = end_time;
        this.search_keywords = search_keywords;
    }
}
