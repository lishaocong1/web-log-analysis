package com.l000phone.bean.common;

import lombok.Data;

/**
 * Task实体类
 */
@Data
public class Task {
    /**
     * 任务编号
     */
    private  int task_id;
    /**
     * 任务名
     */
    private String task_name;
    /**
     * 创建时间
     */
    private String create_time;
    /**
     * 开始运行的时间
     */
    private String start_time;
    /**
     * 结束运行的时间
     */
    private String finish_time;
    /**
     * 任务类型,就是说,在一套大数据平台中,肯定会有各种不同类型的统计分析任务
     * 如说用户访问session分析任务,页面单跳转化率统计任务;所以这个字段就标识了
     * 任务的类型
     */
    private String task_type;
    /**
     * 任务状态，任务对应的就是一次Spark作业的运行，这里就标识了，Spark作业是新建，
     * 还没运行，还是正在运行，还是已经运行完毕
     */
    private String task_status;
    /**
     * 最最重要,用来使用JSON的格式,来封装用户提交的任务对应的特殊的筛选参数
     */
    private String task_param;
}
