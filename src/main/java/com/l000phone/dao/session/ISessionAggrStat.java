package com.l000phone.dao.session;

import com.l000phone.bean.session.SessionAggrStat;

/**
 * session聚合统计数据访问层接口
 */
public interface ISessionAggrStat {
    /**
     * 将session聚合统计实例保存到db中
     */
    void saveBeanToDB(SessionAggrStat bean);
}
