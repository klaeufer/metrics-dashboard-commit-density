package edu.luc.cs.metrics.defect.density.service



import java.time.temporal.TemporalAdjusters
import java.time._

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Properties

/**
 * Created by shilpika on 9/18/15.
 */
object TestObj extends App{
  /*Test.testIssue("Abjad_abjad_Issues","week")
  Test.testIssue("Abjad_abjad_Issues","month")*/
  Test.testKloc("sshilpika_metrics-test_master","month")
  //Test.testKloc("Abjad_abjad_master","month")


}
object Test {

  def testIssue(dbName : String , groupBy: String, klocLis:Map[Instant,(Instant,Double,(Int,Int))]):Unit ={
    import com.mongodb.casbah.Imports._
    val mongoClient = MongoClient("localhost", 27017)
    val db = mongoClient(dbName)
    val collections = db.collectionNames()
    println("Total No of collections"+collections.size)
    val filteredCol = collections.filter(!_.equals("system.indexes"))
    println("Filtered collections "+filteredCol.size)

    //get the total issue count in each collection
    val issueCount = filteredCol.flatMap(coll =>{
      val document = db(coll)
      val documentLis = document.find().sort(MongoDBObject("date"-> 1)).toList map(y => {
        IssueInfo(y.getAs[String]("date") get,y.getAs[String]("state") get)
      })

      val lisWithIssuesUnsorted = documentLis.flatMap(issueDoc => {
        val inst = Instant.parse(issueDoc.date)
        val ldt = ZonedDateTime.ofInstant(inst, ZoneId.of("UTC"))

        val startDate2 =
          if (groupBy.equals("week")) {
            //weekly
            inst.minus(java.time.Duration.ofDays(ldt.getDayOfWeek.getValue - 1))
          } else {
            //monthly
            ldt.`with`(TemporalAdjusters.firstDayOfMonth()).toInstant //inst.minus(Duration.ofDays(ldt.getDayOfMonth-1))
          }

        val startDate1 = ZonedDateTime.ofInstant(startDate2, ZoneId.of("UTC")).withHour(0).withMinute(0).withSecond(0).toInstant
        //println(startDate1+" THis is start date!!!!!")
        //if(klocLis.keys.exists(_ == startDate1)) {
          val startD = klocLis.keys.toList.filter(x => {
            /*println(x.toString+" This is x string "+startDate1);*/ x.toString.contains(startDate1.toString.substring(0, 11))
          })
          //println("STARDDDDD "+startD)
          val startCheck = startD(0)
          val mapValue = klocLis get startCheck get //OrElse(0)
          val openState = if (issueDoc.state.equals("open")) mapValue._3._1 + 1 else mapValue._3._1
          val closeState = if (issueDoc.state.equals("close")) mapValue._3._2 + 1 else mapValue._3._2
          klocLis + (startCheck ->(mapValue._1, mapValue._2, (openState, closeState)))

      })groupBy(y => y._1)

      lisWithIssuesUnsorted.map(y => (y._1,y._2.foldLeft((Instant.now(),0.0D,(0,0)):(Instant,Double,(Int,Int))){(acc,z) => (z._2._1,z._2._2,(z._2._3._1+acc._3._1,z._2._3._2+acc._3._2))}))

      }).toList

    val jsonifyRes = issueCount.map(y => {
      val totalRange = (java.time.Duration.between(y._1,y._2._1).toMillis).toDouble/1000
      LocIssue(y._1.toString, y._2._1.toString,((y._2._2)/1000)/totalRange,IssueState(y._2._3._1,y._2._3._2))
    }).toList

    import JProtocol._
   // jsonifyRes.sortBy(_.startDate).toJson

    println("Total Issues = "+issueCount)

  }

  def testKloc(dbName: String, groupBy:String):Unit ={
    import com.mongodb.casbah.Imports._
    val mongoClient = MongoClient("localhost", 27017)
    val db = mongoClient(dbName)
    val collections = db.collectionNames()
    println("Total No of collections"+collections.size)
    val filteredCol = collections.filter(!_.equals("system.indexes"))
    println("Filtered collections "+filteredCol.size)

    // commits count for files
    val commitCount = filteredCol.flatMap(coll =>{
      val eachColl = db(coll)
      val documentLis = eachColl.find().sort(MongoDBObject("date"-> 1)).toList map(y => {
        CommitInfo(y.getAs[String]("date") get,y.getAs[Int]("loc") get,y.getAs[String]("filename") get,y.getAs[Long]("rangeLoc").getOrElse(0L))})
      // documentLis is the list of documents in the collection. NOTE that these documents are all sorted in ascending order !!!!
      //transform sorted document list into list of sorted dateRange for the file(document in the DB)
      val startDateForFile = documentLis(0).date
      val inststartDateForFile = Instant.parse(startDateForFile)
      val ldt = ZonedDateTime.ofInstant(inststartDateForFile, ZoneId.of("UTC")).withHour(0).withMinute(0).withSecond(0)
      val startDate_groupBy_file =
        if (groupBy.equals("week")) {
          //weekly
          val temp = inststartDateForFile.minus(java.time.Duration.ofDays(ldt.getDayOfWeek.getValue - 1))
          ZonedDateTime.ofInstant(temp, ZoneId.of("UTC")).withHour(0).withMinute(0).withSecond(0).toInstant
        } else {
          //monthly
          ldt.`with`(TemporalAdjusters.firstDayOfMonth()).toInstant
        }
      //generate empty list to hold all values for the files commit history (in other words, generate an empty list for each collection to hold values of documents
      val now = Instant.now()
      val dateRangeLength = if (groupBy.equals("week")) {
        //weekly
        val daysBtw = java.time.Duration.between(startDate_groupBy_file, now).toDays
        if (daysBtw % 7 != 0)
          (daysBtw / 7).toInt + 1
        else
          (daysBtw / 7).toInt
      } else {
        //monthly
        val zonedStart = ZonedDateTime.ofInstant(startDate_groupBy_file, ZoneId.of("UTC"))
        val zonedNow = ZonedDateTime.ofInstant(now, ZoneId.of("UTC"))
        import java.time.temporal.ChronoUnit
        ChronoUnit.MONTHS.between(zonedStart,zonedNow).toInt +1
      }
      //empty list to iterate and fill with valid values, currently contains dummy values
      val l1 = List.fill(dateRangeLength)(("SD", "ED", 0.0D, (0, 0)))

      val dateRangeList = l1.scanLeft((startDate_groupBy_file, startDate_groupBy_file, 0.0D, (0, 0)))((a, x) => {
        if (groupBy.equals("week")) {
          val startOfWeek = ZonedDateTime.ofInstant(a._2, ZoneId.of("UTC")).withHour(0).withMinute(0).withSecond(0)
          val endOfWeek = ZonedDateTime.ofInstant(a._2.plus(java.time.Duration.ofDays(7)), ZoneId.of("UTC")).withHour(23).withMinute(59).withSecond(59)
          (startOfWeek.toInstant, endOfWeek.toInstant, x._3, x._4)
        } else {
          val localDT = ZonedDateTime.ofInstant(a._2, ZoneId.of("UTC")).withHour(0).withMinute(0).withSecond(0)
          val firstDayOfMonth =
            if (a._2 == startDate_groupBy_file)
              localDT.`with`(TemporalAdjusters.firstDayOfMonth())
            else
              localDT.`with`(TemporalAdjusters.firstDayOfNextMonth())
          val lastDayOfMonth = firstDayOfMonth.`with`(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59)
          (firstDayOfMonth.toInstant(), lastDayOfMonth.toInstant(), x._3, x._4)
        }
      }).tail

      var previousLoc = 0
      val lastDateOfCommit = Instant.parse(documentLis(documentLis.length - 1).date)
      val finalres = dateRangeList.map(x => {
        val commitInfoForRange1 = documentLis.filter { dbVals => {
          val ins = Instant.parse(dbVals.date); ins.isAfter(x._1) && ins.isBefore(x._2)
        }
        }.sortBy(_.date)
        val res = if (!commitInfoForRange1.isEmpty) {
          // if commitsInfo is present within the range
          val commitInfoForRange = CommitInfo(x._1.toString, previousLoc, "", java.time.Duration.between(x._1, Instant.parse(commitInfoForRange1(0).date)).toMillis / 1000) +:
            commitInfoForRange1
          val commitInfoForRange2 = commitInfoForRange.take(commitInfoForRange.length - 1) :+ CommitInfo(commitInfoForRange.last.date, commitInfoForRange.last.loc,
            commitInfoForRange.last.filename, java.time.Duration.between(Instant.parse(commitInfoForRange.last.date), x._2).toMillis / 1000)
          previousLoc = commitInfoForRange(commitInfoForRange.length - 1).loc
          val rangeCalulated = commitInfoForRange2.foldLeft(0L: Long) { (a, commitInf) => a + (commitInf.rangeLoc * commitInf.loc)}
          (x._1, x._2, rangeCalulated.toDouble, x._4)
        } else if (x._1.isAfter(lastDateOfCommit)) {
          // if the range falls after the last commit for the file
          val rangeLoc = (documentLis(documentLis.length - 1).loc) * ((java.time.Duration.between(x._1, x._2).toMillis) toDouble) / 1000
          (x._1, x._2, rangeLoc, x._4)
        } else if (x._1.isAfter(inststartDateForFile) && x._1.isBefore(lastDateOfCommit)) {
          // if the range falls inside the commits lifetime of the file but is empty
          val commLis = documentLis.filter { dbVals => {
            val ins = Instant.parse(dbVals.date); ins.isBefore(x._1)
          }
          }
          val rangeLoc = (commLis.sortBy(_.date).reverse(0).loc) * ((java.time.Duration.between(x._1, x._2).toMillis) toDouble) / 1000
          (x._1, x._2, rangeLoc, x._4)
        } else {
          (x._1, x._2, 0.0D, x._4)
        }
        res
      })

      //println(finalres)


      println(documentLis +"\t $$$$$$$$ \t"+finalres)


      println("\n\n")
      finalres

    }).toList
    val result = commitCount.groupBy(_._1) map(y => (y._1,{
      //println("Flatten")
      val rangeLoc = y._2.foldLeft(0D)((acc,z) => acc+z._3)
      (y._2(0)._2,rangeLoc,y._2(0)._4)
    }))
    result
    println("Total Commits = "+result)
  }
}
