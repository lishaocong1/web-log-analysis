package com.l000phone.dao.session;

import com.l000phone.bean.session.Top10CategorySession;

/**
 * 存储top10每个品类的点击top10的session的数据访问层接口
 */
public interface ITop10CategorySession {
    void saveToDB(Top10CategorySession bean);
}
