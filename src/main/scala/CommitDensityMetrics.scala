import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.time.{Duration, LocalDateTime, ZoneId, Instant}
import java.time.temporal.{TemporalAdjusters, ChronoUnit}
import akka.actor.Actor
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocumentReader, BSONDocument}
import spray.routing.HttpService
import spray.json._
import DefaultJsonProtocol._
import util._
import concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Future}

/**
 * Created by sshilpika on 6/30/15.
 */

case class JsonPResult(commitInfo: JsValue)

object JsonPResultProtocol {
  import spray.json.DefaultJsonProtocol._
  implicit val gitResult = jsonFormat(JsonPResult,"commitInfo")
}
case class CommitInfo(date: String,loc: Int, filename: String, rangeLoc: Long)

object CommitInfo{
  implicit object PersonReader extends BSONDocumentReader[CommitInfo]{
    def read(doc: BSONDocument): CommitInfo = {
      val date = doc.getAs[String]("date").get
      val loc = doc.getAs[Int]("loc").get
      val filename = doc.getAs[String]("filename").get
      val rangeLoc = doc.getAs[Long]("rangeLoc").get
      CommitInfo(date,loc,filename,rangeLoc)
    }
  }
}

case class IssueInfo(date: String,state: String)

object IssueInfo{
  implicit object PersonReader extends BSONDocumentReader[IssueInfo]{
    def read(doc: BSONDocument): IssueInfo = {
      val date = doc.getAs[String]("date").get
      val state = doc.getAs[String]("state").get
      IssueInfo(date,state)
    }
  }
}
object ReactiveMongo {
  //get an instance of the driver
  //(creates an actor system)
  val driver = new MongoDriver
  val connection = driver.connection(List("localhost"))
}

trait CommitDensityService extends HttpService{
  val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")


  def getIssues(user: String, repo: String, branch:String, groupBy: String, klocList:Map[Instant,(Instant,Long,(Int,Int))]): Future[String] ={
    val driver = ReactiveMongo.driver
    val connection = ReactiveMongo.connection
    //gets a reference of the database for commits
    val db = connection.db(user+"_"+repo+"_Issues")
    val collection = db.collectionNames
    val added = collection.map(p => p.filter(!_.contains("system.indexes")))
    //val sorted = added.map(p => p.sorted)

    val finalRes = added.flatMap(p =>{
      //println(p)
      val res = Future.sequence(p.map(collName => {
        val coll = db.collection[BSONCollection](collName)
        val issuesList = coll.find(BSONDocument()).sort(BSONDocument("date" -> 1)).cursor[IssueInfo].collect[List]()
        issuesList.map(issueDocList => {
          issueDocList.map(issueDoc => {
            val inst = Instant.parse(issueDoc.date)
            val ldt = LocalDateTime.ofInstant(inst,ZoneId.of("UTC"))
            val startDate = if(groupBy.equals("week")) //weekly
              inst.minus(Duration.ofDays(ldt.getDayOfWeek.getValue-1))
            else{//monthly
              inst.minus(Duration.ofDays(ldt.getDayOfMonth-1))
            }
            //println(klocList+"!!!!!!!!!!"+startDate.toString.substring(0,11)+"!!!!!!!!!!"+klocList.keys.toList.filter(x => x.toString.contains(startDate.toString.substring(11))))
            val startD = klocList.keys.toList.filter(x => x.toString.contains(startDate.toString.substring(0,11)))(0)
            val mapValue =  klocList get startD  get//OrElse(0)
            val openState =if(issueDoc.state.equals("open")) mapValue._3._1+1 else mapValue._3._1
            val closeState = if(issueDoc.state.equals("close")) mapValue._3._2+1 else mapValue._3._2
            klocList + (startD -> (mapValue._1,mapValue._2,(openState,closeState)))

          })
        })

      })).map(_.flatten)
      //println(res)
      res.map(p => {
        val x = p.map(x => x.toList).flatten.groupBy(y => y._1)
        x.map(y => (y._1,y._2.foldLeft((Instant.now(),0L,(0,0)):(Instant,Long,(Int,Int))){(acc,z) => (z._2._1,z._2._2,(z._2._3._1+acc._3._1,z._2._3._2+acc._3._2))}))
        //c.map
      })

    })
    finalRes.map(_.toString)
  }

  def getKloc(user: String, repo: String, branch:String, groupBy: String): Future[Map[Instant,(Instant,Long,(Int,Int))]] = {
    import reactivemongo.api._
    val driver = ReactiveMongo.driver
    val connection = ReactiveMongo.connection

    //gets a reference of the database for commits
    val db = connection.db(user+"_"+repo+"_"+branch)
    val collection = db.collectionNames
    val added = collection.map(p => p.filter(!_.contains("system.indexes")))
    //val sorted = added.map(p => p.sorted)


    val collInfo = added.flatMap(p => {
      //res1 gets data from the DB and saves it in a list of (commitDate, LOC, weekStartDate, weekEndDate)
      val res1 = Future.sequence(p.map(collName=> {
        val coll = db.collection[BSONCollection](collName)
        val collectionsList = coll.find(BSONDocument()).sort(BSONDocument("date" -> 1)).cursor[CommitInfo].collect[List]()

        val res = collectionsList.map(p => {
          //finding the start date of the week for this file
          val inst = Instant.parse(p(0).date)
          val ldt = LocalDateTime.ofInstant(inst,ZoneId.of("UTC"))
          val startDate =
            if(groupBy.equals("week")) //weekly
              inst.minus(Duration.ofDays(ldt.getDayOfWeek.getValue-1))
            else{//monthly
              inst.minus(Duration.ofDays(ldt.getDayOfMonth-1))
            }
          val now = Instant.now()
          //val nowLdt = LocalDateTime.ofInstant(now,ZoneId.of("UTC"))
          //val startDateLdt = LocalDateTime.ofInstant(startDate,ZoneId.of("UTC"))

          val dateRangeLength =if(groupBy.equals("week")) //weekly
            (Duration.between(startDate, now).toDays/7).toInt+1
          else{//monthly
            (Duration.between(startDate, now).toDays/30).toInt+1
          }
          val l1 = List.fill(dateRangeLength)(("SD","ED",0L,(0,0))) // this is the tuple containing(startDate,EndDate,RangleLoc,(IssueOpen,IssueClosed)

          val dateRangeList = l1.scanLeft((startDate,startDate,0L,(0,0)))((a,x)=> {
            if(groupBy.equals("week"))
              (a._2,a._2.plus(Duration.ofDays(7)),x._3,x._4)
            else{
              val localDT = LocalDateTime.ofInstant(a._2,ZoneId.of("UTC"))
              val offset = localDT.atZone(ZoneId.of("UTC")).getOffset
              val firstDayOfMonth =
                if(a._2== startDate)
                  localDT.`with`(TemporalAdjusters.firstDayOfMonth())
                else
                  localDT.`with`(TemporalAdjusters.firstDayOfNextMonth())
              val lastDayOfMonth = firstDayOfMonth.`with`(TemporalAdjusters.lastDayOfMonth())
              (firstDayOfMonth.toInstant(offset),lastDayOfMonth.toInstant(offset),x._3,x._4)
            }
          })
          //p is the list of CommitsInfo sorted by Date
          var previousLoc = 0
          val rangeLocList = dateRangeList.map( x => {
           // println(p)
            val commitInfoForRange1 = p.filter{dbVals => {val ins = Instant.parse(dbVals.date); ins.isAfter(x._1) && ins.isBefore(x._2)  }}
            if(!commitInfoForRange1.isEmpty) {
              val commitInfoForRange = CommitInfo(x._1.toString, previousLoc, "", Duration.between(x._1, Instant.parse(commitInfoForRange1(0).date)).toMillis / 1000) +: commitInfoForRange1
              val commitInfoForRange2 = commitInfoForRange.take(commitInfoForRange.length - 1) :+ CommitInfo(commitInfoForRange.last.date, commitInfoForRange.last.loc,
                commitInfoForRange.last.filename, Duration.between(Instant.parse(commitInfoForRange.last.date), x._2).toMillis / 1000)
              previousLoc = commitInfoForRange(commitInfoForRange.length - 1).loc
              val rangeCalulated = commitInfoForRange2.foldLeft(0L: Long) { (a, commitInf) => a + (commitInf.rangeLoc * commitInf.loc)}
              (x._1, x._2, rangeCalulated, x._4)
            }else (x._1, x._2,0L,x._4)
          })
          rangeLocList
        })

        res
      }))
      res1.map(p => {
        //p.groupBy(x => x)
        p.flatten.groupBy(x => x._1).map(y => (y._1,{
          val rangeLoc = y._2.foldLeft(0L)((acc,z) => acc+z._3)
          (y._2(0)._2,rangeLoc,y._2(0)._4)
        }))
      })
    })
    collInfo

  }

  def dataForDensityMetrics(user: String, repo: String, branch:String, groupBy: String): Future[String] ={

    val kloc = getKloc(user, repo, branch, groupBy)
    kloc.flatMap(kloc1 => {
      val writer = new PrintWriter(new java.io.File("store.txt"))
      writer.write(kloc1.toString())
      writer.close()
      getIssues(user, repo, branch, groupBy, kloc1)})

  }




  val myRoute = path(Segment / Segment / Segment) { ( user, repo, branch) =>
    get {
        optionalHeaderValueByName("Authorization") { (accessToken) =>
          jsonpWithParameter("jsonp") {
            import JsonPResultProtocol._
            import spray.httpx.SprayJsonSupport._
            parameters('groupBy ? "week") {(groupBy) =>
              onComplete(dataForDensityMetrics(user, repo, branch, groupBy)) {
                case Success(value) => complete(value)
                case Failure(value) => complete("Request to github failed with value : " + value)

              }
            }
          }
        }
    }
  }
}

class MyServiceActor extends Actor with CommitDensityService {

  def actorRefFactory = context

  def receive = runRoute(myRoute)
}
