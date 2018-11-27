package com.l000phone.dao.common.impl;

import com.l000phone.bean.common.Task;
import com.l000phone.dao.common.ITaskDao;
import com.l000phone.util.DBCPUtil;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;

import java.sql.SQLException;

/**
 *任务表Task数据访问层操作实现类
 */
public class TaskDaoImpl implements ITaskDao {
    private QueryRunner qr=new QueryRunner(DBCPUtil.getDataSource());
    @Override
    public Task findTaskById(int taskId) {
        try {
            return qr.query("select * from task where task_id=?",new BeanHandler<Task>(Task.class),taskId);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
