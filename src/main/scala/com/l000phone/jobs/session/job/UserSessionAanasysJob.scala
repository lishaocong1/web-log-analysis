package com.l000phone.jobs.session.job

import java.text.SimpleDateFormat

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import com.l000phone.bean.common.{Task, TaskParam}
import com.l000phone.bean.session._
import com.l000phone.constant.Constants
import com.l000phone.dao.common.ITaskDao
import com.l000phone.dao.common.impl.TaskDaoImpl
import com.l000phone.dao.session.{ISessionDetail, ISessionRandomExtract, ITop10Category, ITop10CategorySession}
import com.l000phone.dao.session.impl._
import com.l000phone.jobs.session.accumulator.SessionAggrStatAccumulator
import com.l000phone.jobs.session.bean.CategoryBean
import com.l000phone.mock.MockData
import com.l000phone.util.{ResourcesUtils, StringUtils}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession.Builder
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.sql.{Row, SparkSession}

import scala.collection.mutable.ArrayBuffer

/**
  * 用户session分析模块
  */
object UserSessionAanasysJob {

  def main(args: Array[String]): Unit = {
    //前提
    val spark = prepareOperate(args)
    //步骤:
    //1.按条件筛选session
    filterSessionByCondition(spark,args)
    //    2、统计出符合条件的session中，访问时长在1s~3s、4s~6s、7s~9s、10s~30s、30s~60s、1m~3m、3m~10m、
    //    10m~30m、30m以上各个范围内的session占比；访问步长在1~3、4~6、7~9、10~30、30~60、60以上各个
    //      范围内的session占比
    getStepLenAndTimeLenRate(spark,args)
    //3.在符合条件的session中,按照时间比例随机抽取1000个session
      randomExtract1000Session(spark,args)
    //    4、在符合条件的session中，获取点击、下单和支付数量排名前10的品类
                 val categoryIdContainer: ArrayBuffer[Long] = calClickOrderPayTop10(spark, args)
    //    5、对于排名前10的品类，分别获取其点击次数排名前10的session
         calTop10ClickCntSession(categoryIdContainer, spark, args)
  }

  /**
    * 准备操作
    * @param args
    * @return
    */
  def prepareOperate(args: Array[String]) ={
    //0.拦截非法的操作
    if(args==null||args.length!=1){
      print("参数录入错误或是没有准备参数!请使用:spark-submit主类 jar taskId")
      System.exit(-1)
    }
    //1.SparkSession的实例(注意:若分析的是hive表,需要启用对hive的支持,Builder的实例.enableHiveSupport())
    val builder: Builder = SparkSession.builder().appName(UserSessionAanasysJob.getClass.getSimpleName)
    //若是本地集群模式,需要单独设置
    if (ResourcesUtils.dMode.toString.toLowerCase().equals("local")){
      builder.master("local[*]")
    }
    val spark: SparkSession = builder.getOrCreate()
    //2将模拟的数据装载进内存(hive 表中的数据)
    MockData.mock(spark.sparkContext,spark.sqlContext)
    //3.设置日志的显示级别
    spark.sparkContext.setLogLevel("WARN")
    //模拟数据测试
    //spark.sql("select * from user_visit_action").show(1000)
    //4.返回sparksession的实例
    spark
  }

  /**
    * 按照条件筛选session
    */
  def filterSessionByCondition(spark: SparkSession, args: Array[String]) = {
    //①准备一个字符串构建器的实例StringBuffer，用于存储sql
    val buffer = new StringBuffer
    buffer.append("select u.session_id,u.action_time,u.search_keyword,i.user_id,u.page_id,u.click_category_id,u.click_product_id,u.order_category_ids,u.order_product_ids,u.pay_category_ids,u.pay_product_ids from  user_visit_action u,user_info i where u.user_id=i.user_id ")

    //②根据从mysql中task表中的字段task_param查询到的值，进行sql语句的拼接
    val taskId = args(0).toInt
    val taskDao: ITaskDao = new TaskDaoImpl
    val task: Task = taskDao.findTaskById(taskId)

    // task_param={"ages":[0,100],"genders":["男","女"],"professionals":["教师", "工人", "记者", "演员", "厨师", "医生", "护士", "司机", "军人", "律师"],"cities":["南京", "无锡", "徐州", "常州", "苏州", "南通", "连云港", "淮安", "盐城", "扬州"]})
    val taskParamJsonStr = task.getTask_param

    //使用FastJson，将json对象格式的数据封装到实体类TaskParam中
    val taskParam: TaskParam = JSON.parseObject[TaskParam](taskParamJsonStr, classOf[TaskParam])

    //获得参数值
    val ages = taskParam.getAges
    val genders = taskParam.getGenders
    val professionals = taskParam.getProfessionals
    val cities = taskParam.getCities

    //ages
    if (ages != null && ages.size() > 0) {
      val minAge = ages.get(0)
      val maxAge = ages.get(1)
      buffer.append(" and i.age between ").append(minAge + "").append(" and ").append(maxAge + "")
    }

    //genders
    if (genders != null && genders.size() > 0) {
      //希望sql: ... and i.sex in('男','女')
      //JSON.toJSONString(genders, SerializerFeature.UseSingleQuotes)~> ['男','女']
      buffer.append(" and i.sex  in(").append(JSON.toJSONString(genders, SerializerFeature.UseSingleQuotes).replace("[", "").replace("]", "")).append(")")
    }

    //professionals
    if (professionals != null && professionals.size() > 0) {
      buffer.append(" and i.professional  in(").append(JSON.toJSONString(professionals, SerializerFeature.UseSingleQuotes).replace("[", "").replace("]", "")).append(")")
    }

    //cities
    if (cities != null && cities.size() > 0) {
      buffer.append(" and i.city in(").append(JSON.toJSONString(cities, SerializerFeature.UseSingleQuotes).replace("[", "").replace("]", "")).append(")")
    }

    //③测试，然后将结果注册为一张临时表，供后续的步骤使用（为了提高速度：需要将临时表缓存起来）
    //println("sql语句："+buffer.toString)
    //spark.sql(buffer.toString).show(2000)
    spark.sql(buffer.toString).createOrReplaceTempView("filter_after_action")
    spark.sqlContext.cacheTable("filter_after_action")

    //spark.sql("select * from filter_after_action").show(1000)
  }

  /**
    *
    * 统计出符合条件的session中，访问时长在1s~3s、4s~6s、7s~9s、10s~30s、30s~60s、1m~3m、3m~10m、
    * 10m~30m、30m以上各个范围内的session占比；访问步长在1~3、4~6、7~9、10~30、30~60、60以上各个
    * 范围内的session占比
    *
    */
  def getStepLenAndTimeLenRate(spark: SparkSession, args: Array[String]) = {
    //①根据session_id进行分组，求出各个session的步长和时长
    //注册自定义函数
    spark.udf.register("getTimeLen",(endTime:String,startTime:String)=> getTimeLen(endTime, startTime))
    val rdd: RDD[Row] = spark.sql("select count(*) stepLen,getTimeLen(max(action_time),min(action_time)) timeLen from filter_after_action group by session_id").rdd

    //准备一个自定义累加器的实例,并进行注册
    val acc: SessionAggrStatAccumulator = new SessionAggrStatAccumulator
    spark.sparkContext.register(acc)
    //②将结果转换成rdd，循环分析RDD
    rdd.collect.foreach(row=>{
      //循环体
      //session_count累加1
      acc.add(Constants.SESSION_COUNT)
      //将当前的步长与各个步长进行比对，若吻合，当前的步长累加1
      calStepLenSessionCnt(row, acc)

      //将当前的时长与各个时长进行比对，若吻合，当前的时长累加1
      calTimeLenSessionCnt(row, acc)
    })
    //③将最终的结果保存到db中的session_aggr_stat表
    saveSessionAggrStatToDB(acc, args)
  }

  /**
    * 将最终的结果保存到db中的session_aggr_stat表
    */
  def saveSessionAggrStatToDB(acc: SessionAggrStatAccumulator, args: Array[String]) = {
    //session_count=189|1s_3s=20|4s_6s...|60=90
    val finalResult = acc.value
    val session_count = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.SESSION_COUNT).toInt
    val period_1s_3s = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.TIME_PERIOD_1s_3s).toDouble / session_count
    val period_4s_6s = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.TIME_PERIOD_4s_6s).toDouble / session_count
    val period_7s_9s = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.TIME_PERIOD_7s_9s).toDouble / session_count
    val period_10s_30s = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.TIME_PERIOD_10s_30s).toDouble / session_count
    val period_30s_60s = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.TIME_PERIOD_30s_60s).toDouble / session_count
    val period_1m_3m = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.TIME_PERIOD_1m_3m).toDouble / session_count
    val period_3m_10m = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.TIME_PERIOD_3m_10m).toDouble / session_count
    val period_10m_30m = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.TIME_PERIOD_10m_30m).toDouble / session_count
    val period_30m = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.TIME_PERIOD_30m).toDouble / session_count
    val step_1_3 = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.STEP_PERIOD_1_3).toDouble / session_count
    val step_4_6 = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.STEP_PERIOD_4_6).toDouble / session_count
    val step_7_9 = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.STEP_PERIOD_7_9).toDouble / session_count
    val step_10_30 = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.STEP_PERIOD_10_30).toDouble / session_count
    val step_30_60 = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.STEP_PERIOD_30_60).toDouble / session_count
    val step_60 = StringUtils.getFieldFromConcatString(finalResult, "\\|", Constants.STEP_PERIOD_60).toDouble / session_count
    val bean: SessionAggrStat = new SessionAggrStat(args(0).toInt, session_count, period_1s_3s, period_4s_6s, period_7s_9s, period_10s_30s, period_30s_60s, period_1m_3m, period_3m_10m, period_10m_30m, period_30m, step_1_3, step_4_6, step_7_9, step_10_30, step_30_60, step_60)
    //println(finalResult)
    val dao: SessionAggrStatImpl = new SessionAggrStatImpl
    dao.saveBeanToDB(bean)
  }

  /**
    * 求相应时长范围内的session数
    */
  def calTimeLenSessionCnt(row: Row, acc: SessionAggrStatAccumulator) = {
    val nowTimeLen = row.getAs[Long]("timeLen")
    val timeLenSeconds = nowTimeLen / 1000
    val timeLenMinutes = timeLenSeconds / 60


    if (timeLenSeconds >= 1 && timeLenSeconds <= 3) {
      acc.add(Constants.TIME_PERIOD_1s_3s)
    } else if (timeLenSeconds >= 4 && timeLenSeconds <= 6) {
      acc.add(Constants.TIME_PERIOD_4s_6s)
    } else if (timeLenSeconds >= 7 && timeLenSeconds <= 9) {
      acc.add(Constants.TIME_PERIOD_7s_9s)
    } else if (timeLenSeconds >= 10 && timeLenSeconds <= 30) {
      acc.add(Constants.TIME_PERIOD_10s_30s)
    } else if (timeLenSeconds > 30 && timeLenSeconds < 60) {
      acc.add(Constants.TIME_PERIOD_30s_60s)
    } else if (timeLenMinutes >= 1 && timeLenMinutes < 3) {
      acc.add(Constants.TIME_PERIOD_1m_3m)
    } else if (timeLenMinutes >= 3 && timeLenMinutes < 10) {
      acc.add(Constants.TIME_PERIOD_3m_10m)
    } else if (timeLenMinutes >= 10 && timeLenMinutes < 30) {
      acc.add(Constants.TIME_PERIOD_10m_30m)
    } else if (timeLenMinutes >= 30) {
      acc.add(Constants.TIME_PERIOD_30m)
    }
  }

  /**
    * 求session在相应步长中的个数
    */
  def calStepLenSessionCnt(row: Row, acc: SessionAggrStatAccumulator) = {
    val nowStepLen = row.getAs[Long]("stepLen")
    if (nowStepLen >= 1 && nowStepLen <= 3) {
      acc.add(Constants.STEP_PERIOD_1_3)
    } else if (nowStepLen >= 4 && nowStepLen <= 6) {
      acc.add(Constants.STEP_PERIOD_4_6)
    } else if (nowStepLen >= 7 && nowStepLen <= 9) {
      acc.add(Constants.STEP_PERIOD_7_9)
    } else if (nowStepLen >= 10 && nowStepLen <= 30) {
      acc.add(Constants.STEP_PERIOD_10_30)
    } else if (nowStepLen > 30 && nowStepLen <= 60) {
      acc.add(Constants.STEP_PERIOD_30_60)
    } else if (nowStepLen > 60) {
      acc.add(Constants.STEP_PERIOD_60)
     }
    }

  /**
    * 获取时长值
    * endTime
    * startTime,刑如:2018-11-27 11:59:47
    * return 返回毫秒值
    */
  def  getTimeLen(endTime: String, startTime: String): Long = {
    val sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    sdf.parse(endTime).getTime-sdf.parse(startTime).getTime
  }

  /**
    * 在符合条件的session中,按照时间比例随机抽取1000个session
    */
  def randomExtract1000Session(spark: SparkSession,args: Array[String]) ={
    //前提:准备一个容器,用于存储session_id
    val container:ArrayBuffer[String] = new ArrayBuffer
    val bcContainer: Broadcast[ArrayBuffer[String]] = spark.sparkContext.broadcast[ArrayBuffer[String]](container)

    //1.求出每个时间段内的session数占总session数的比例值(不去重的session数)
    val totalSessionCnt: Long = spark.sql("select count(*)totalSessionCnt from filter_after_action").first().getAs[Long]("totalSessionCnt")
    val rdd: RDD[Row] = spark.sql("select substring(action_time,1,13)timePeriod,count(*)/"+totalSessionCnt.toDouble+" rateValue  from filter_after_action group by substring(action_time,1,13)").rdd
    //将taskId封装到广播变量中
    val bcTaskId: Broadcast[Int] = spark.sparkContext.broadcast[Int](args(0).toInt)
    //②根据比例值rdd，从指定的时段内随机抽取相应数量的session,并变形后保存到db中
    rdd.collect.foreach(
      row => {
        //循环分析rdd,每循环一次
        // 根据比率值从filter_after_action抽取session
        extractSessionByRate(row, spark, totalSessionCnt)
        // 将结果映射为一张临时表，聚合后保存到db中
        aggrResultToDB(spark, bcTaskId, bcContainer)
      })

    //③向存储随机抽取出来的session的明细表中存储数据
    //容器中存取的session_id与filter_after_action表进行内连接查询，查询处满足条件的记录保存到明细表中
    randomSessionToDetail(spark, bcTaskId, bcContainer)
  }

  /**
    * 根据比率值从filter_after_action抽取session
    */
  def extractSessionByRate(row: Row, spark: SparkSession, totalSessionCnt: Long) = {
    val nowTimePeriod = row.getAs[String]("timePeriod")
    val rateValue = row.getAs[java.math.BigDecimal]("rateValue").doubleValue

    val needTotalSessionCnt = if (totalSessionCnt > 1000) 1000 else totalSessionCnt

    val arr: Array[Row] = spark.sql("select session_id,action_time,search_keyword from filter_after_action where instr(action_time,'" + nowTimePeriod + "') >0").rdd.takeSample(true, (needTotalSessionCnt * rateValue).toInt)


    val rdd: RDD[Row] = spark.sparkContext.parallelize(arr)
    val structType: StructType = StructType(Seq(StructField("session_id", StringType, false), StructField("action_time", StringType, false), StructField("search_keyword", StringType, true)))

    spark.createDataFrame(rdd, structType).createOrReplaceTempView("temp_random")
  }

  /**
    * 将结果映射为一张临时表,聚合后保存到db中
    */
  def aggrResultToDB(spark: SparkSession, bcTaskId: Broadcast[Int], bcContainer: Broadcast[ArrayBuffer[String]]) = {
    val nowPeriodAllSessionRDD: RDD[Row] = spark.sql(" select  session_id,concat_ws(',', collect_set(distinct search_keyword)) search_keywords ,min(action_time) start_time,max(action_time) end_time from temp_random group by session_id").rdd
    nowPeriodAllSessionRDD.foreachPartition(itr=>{
      if (!itr.isEmpty){
         //将迭代器中的记录取出来,存储到集合中List<SessionRandomExtract>
         val beans: java.util.List[SessionRandomExtract] = new java.util.LinkedList()
        itr.foreach(row=>{
          val task_id = bcTaskId.value
          val session_id = row.getAs[String]("session_id")
          val start_time = row.getAs[String]("start_time")
          val end_time = row.getAs[String]("end_time")
          val search_keywords = row.getAs[String]("search_keywords")
          val bean = new SessionRandomExtract(task_id,session_id,start_time,end_time,search_keywords)
          beans.add(bean)
          //向容器中存入当前的session_id
          bcContainer.value.append(session_id)
        })
        //准备dao层的实例ISessionRandomExtract
        val dao:ISessionRandomExtract = new SessionRandomExtractImpl
        //调用其中的方法saveBeansToDB,将当前集合中的所有实例保存到表中
        dao.saveBeansToDB(beans)
      }
    })
  }

  /**
    *  向存储随机抽取出来的session的明细表中存储数据
    */
  def randomSessionToDetail(spark: SparkSession, bcTaskId: Broadcast[Int], bcContainer: Broadcast[ArrayBuffer[String]]) = {
        //将容器映射为一张临时表,与filter_after_action表进行内连接查询
        val rowContainer = spark.sparkContext.parallelize(bcContainer.value).map(perEle=>Row(perEle))
         val structTypeContainer = StructType(Seq(StructField("session_id", StringType, true)))
        spark.createDataFrame(rowContainer,structTypeContainer).createOrReplaceTempView("container_temp")
        val dao: ISessionDetail = new SessionDetailImpl
           spark.sql("select * from container_temp t,filter_after_action f where t.session_id=f.session_id").rdd.collect.foreach(row=>{
                   val task_id = bcTaskId.value
                   val user_id = row.getAs[Long]("user_id").toInt
                   val session_id = row.getAs[String]("session_id")
                   val page_id = row.getAs[Long]("page_id").toInt
                   val action_time = row.getAs[String]("action_time")
                   val search_keyword = row.getAs[String]("search_keyword")
                   val click_category_id = row.getAs[Long]("click_category_id").toInt
                   val click_product_id = row.getAs[Long]("click_product_id").toInt
                   val order_category_ids = row.getAs[String]("order_category_ids")
                   val order_product_ids = row.getAs[String]("order_product_ids")
                   val pay_category_ids = row.getAs[String]("pay_category_ids")
                   val pay_product_ids = row.getAs[String]("pay_product_ids")
             val bean: SessionDetail = new SessionDetail(task_id, user_id, session_id, page_id, action_time, search_keyword, click_category_id, click_product_id, order_category_ids, order_product_ids, pay_category_ids, pay_product_ids)
                dao.saveToDB(bean)
           })
  }

  /**
    * 订单支付品类top10
    */
   def calClickOrderPayTop10(spark: SparkSession, args: Array[String]) = {
        //前提
     //1.准备一个容器
     val container:ArrayBuffer[CategoryBean]=new ArrayBuffer[CategoryBean]()
     //2.准备一个容器,存放点击,下单和支付数量排名前10的品类的id的值,供下一步使用
     val categoryIdContainer:ArrayBuffer[Long]=new ArrayBuffer
     //1.设计一个实体类CategoryBean,需要实现Orderd特质(类似于java的Comparable)
      //2.求出所有品类总的点击次数
        val arr: Array[Row] = spark.sql("select click_category_id, count(*)  total_click_cnt from filter_after_action where click_category_id is not null  group by click_category_id ").rdd.collect

            val rdd = spark.sql("select * from filter_after_action").rdd.cache()
           //3.根据不同品类总的点击次数
     for(row <-arr){
        //点击的品类id
       val click_category_id = row.getAs[Long]("click_category_id").toString
       //总的点击次数
       val total_click_cnt = row.getAs[Long]("total_click_cnt")
       //求该品类总的下单次数
        val total_order_cnt = rdd.filter(row=>click_category_id.equals(row.getAs[String]("order_category_ids"))).count()
       //求该品类总的支付次数
           val total_pay_cnt = rdd.filter(row=>click_category_id.equals(row.getAs[String]("pay_category_ids"))).count()
       //封装成~>  CategoryBean的实例，且添加到容器中存储起来
       val bean = new CategoryBean(click_category_id.toLong,total_click_cnt, total_order_cnt, total_pay_cnt)
       //将实例添加到容器中
         container.append(bean)
     }
         //④将容器转换成RDD,使用spark中的二次排序算子求topN ~>注意点： RDD中每个元素的类型是对偶元组 （对偶元组：将元组中只有两个元素的元组。）
     val arrs: Array[(CategoryBean, String)] = spark.sparkContext.parallelize(container).map(perEle => (perEle, "")).sortByKey().take(10)
     val dao: ITop10Category = new Top10CategoryImpl
        for (tuple<-arrs){
          val tmpBean: CategoryBean = tuple._1
          val bean: Top10Category = new Top10Category(args(0).toInt,tmpBean.getClick_category_id.toInt,tmpBean.getTotal_click_cnt.toInt, tmpBean.getTotal_order_cnt.toInt, tmpBean.getTotal_pay_cnt.toInt)
          dao.saveBeanToDB(bean)
          //向容器中存入top10品类的id
          categoryIdContainer.append(tmpBean.getClick_category_id)
        }
     categoryIdContainer
   }

  /**
    * 对于排名前10的品类,分别获取其点击次数排名前10的session
    */
  def calTop10ClickCntSession(categoryIdContainer: ArrayBuffer[Long], spark: SparkSession, args: Array[String]): Unit ={
    val dao: ITop10CategorySession = new Top10CategorySessionImpl
    for (category_id<-categoryIdContainer){
      spark.sql("select  session_id, count(*) cnt   from filter_after_action where click_category_id=" + category_id + " group by session_id").createOrReplaceTempView("temp_click_session")
      spark.sql("select  * from temp_click_session order by cnt desc").take(10).foreach(row=>{
        val session_id = row.getAs[String]("session_id")
        val click_count = row.getAs[Long]("cnt")
        val bean = new Top10CategorySession(args(0).toInt, category_id.toInt, session_id, click_count.toInt)
        dao.saveToDB(bean)
      })
    }
  }
}
