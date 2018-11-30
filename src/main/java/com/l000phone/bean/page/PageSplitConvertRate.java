package com.l000phone.bean.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PageSplitConvertRate {
    private int task_id;
    //页面转化率
    private String convert_rate;

    public PageSplitConvertRate(int task_id, String convert_rate) {
        this.task_id = task_id;
        this.convert_rate = convert_rate;
    }
}
