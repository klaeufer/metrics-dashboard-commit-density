import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit

import akka.actor.Actor
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocumentReader, BSONObjectID, BSONDocument}
import spray.can.Http
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.routing.HttpService
import spray.http._
import spray.httpx.RequestBuilding._
import spray.json._
import DefaultJsonProtocol._
import spray.routing._
import util._
import concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
//import com.mongodb.casbah.Imports._
import spray.util._
/**
 * Created by sshilpika on 6/30/15.
 */

case class JsonPResult(commitInfo: JsValue)

object JsonPResultProtocol {
  import spray.json.DefaultJsonProtocol._
  implicit val gitResult = jsonFormat(JsonPResult,"commitInfo")
}
case class CommitInfo(date: String,loc: Int, filename: String, since: String, until: String)

object CommitInfo{
  implicit object PersonReader extends BSONDocumentReader[CommitInfo]{
    def read(doc: BSONDocument): CommitInfo = {
      val date = doc.getAs[String]("date").get
      val loc = doc.getAs[Int]("loc").get
      val filename = doc.getAs[String]("filename").get
      val since = doc.getAs[String]("since").get
      val until = doc.getAs[String]("until").get
      CommitInfo(date,loc,filename,since,until)
    }
  }
}

trait CommitDensityService extends HttpService{
  val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

  def connect(repo: String, branch:String, displayOption: String): Future[JsValue] = {
    import reactivemongo.api._

    //get an instance of the driver
    //(creates an actor system)
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost"))

    //gets a reference of the database
    val db = connection.db(repo)
    val collection = db.collectionNames
    val added = collection.map(p => p.filter(_.contains("COLL")))
    val sorted = added.map(p => p.sorted)

    val collInfo = sorted.flatMap(p => {
      //res1 gets data from the DB and saves it in a list of (commitDate, LOC, weekStartDate, weekEndDate)
      val res1 = Future.sequence(p.map(collName=> {
        val coll = db.collection[BSONCollection](collName)
        val collectionsList = coll.find(BSONDocument()).sort(BSONDocument("date" -> 1)).cursor[CommitInfo].collect[List]()

        val res = collectionsList.map(p => {
          val groupedByDate = p.groupBy(x => x.date)
          val tuples = groupedByDate.map{case(date,commitInfoList) => (date,commitInfoList.foldLeft(0){
            (loc, commitInfo) => {
              commitInfo.loc+loc
            }
          },commitInfoList(0).since,commitInfoList(0).until)}
          tuples
        })

        res
      }))





      //below are the calculations for density metrics
      res1.map(p => {
        p.foldLeft(Nil:List[JsValue]){(str, x) => {

          val lis1 = x.toList.sortBy(x => x._1)
          val previousCommitLoc = if(p.indexOf(x)==0){
            0
          }else{
            lis1(lis1.size-1)._2
          }
          val lis2 = (lis1(0)._3, previousCommitLoc,lis1(0)._3,lis1(0)._4)+:lis1
          val lis3 = lis2.tail:+(lis1(0)._4,lis1(lis1.size-1)._2,lis1(0)._3,lis1(0)._4)
          println(lis2)
          println(lis3)
          val lis4 = lis2.zip(lis3)
          println(lis4)
          val result = lis4.map(h => {
            (ChronoUnit.MILLIS.between(sdf.parse(h._1._1).toInstant,sdf.parse(h._2._1).toInstant),h._1._2)
          })
          val totalMillis = result.foldLeft(0L:Long){(l,z) => l+z._1}
          val finalResult = (result.map(z => z._1*z._2).sum)/totalMillis
          val r = finalResult.toDouble/1000

          val startDate = lis1(0)._3
          val endDate = lis1(0)._4

          val resultF = s"""{"start_date": "$startDate", "end_date": "$endDate", "KLOC": $r}""".parseJson
          str:+resultF

        }}
      })
    })
    collInfo.map(x=> x.toJson)

  }




  val myRoute = path(Segment / Segment) { ( repo, branch) =>
    get {
        optionalHeaderValueByName("Authorization") { (accessToken) =>
          jsonpWithParameter("jsonp") {
            import JsonPResultProtocol._
            import spray.httpx.SprayJsonSupport._
            onComplete(connect(repo, branch, "week")) {
              case Success(value) => complete(JsonPResult(value))
              case Failure(value) => complete("Request to github failed with value : " + value)
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
