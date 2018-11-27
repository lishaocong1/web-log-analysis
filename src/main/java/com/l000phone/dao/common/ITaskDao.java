package com.l000phone.dao.common;

import com.l000phone.bean.common.Task;

/**
 * 任务表Task数据访问层操作
 */
public interface ITaskDao {
    /**
     * 根据任务id查询相应的Task信息
     */
    Task findTaskById(int taskId);
}
