package com.l000phone.dao.session;

import com.l000phone.bean.session.SessionRandomExtract;

import java.util.List;

/**
 * 按时间比例随机抽取功能抽取出来的session数据访问层接口
 */
public interface ISessionRandomExtract {
    /**
     * 将容器中所有的实体批量保持到db中
     */
    void saveBeansToDB(List<SessionRandomExtract> beans);
}
