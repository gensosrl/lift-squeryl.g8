package $package$
package config

import net.liftweb._
import common._
import http._
import json._
import util.Props
import net.liftweb.squerylrecord.RecordTypeMode
import RecordTypeMode._
import java.sql.DriverManager
import net.liftmodules.squerylauth.model.DbSchema
import org.squeryl.Schema

object SquerylConfig extends Factory with Loggable {

  private def initH2(schema: () => Schema*) {
    Class.forName("org.h2.Driver")     
    import org.squeryl.adapters.H2Adapter
    import net.liftweb.squerylrecord.SquerylRecord
    import org.squeryl.Session
    SquerylRecord.initWithSquerylSession(Session.create(
      DriverManager.getConnection("jdbc:h2:mem:dbname;DB_CLOSE_DELAY=-1", "sa", ""),
      new H2Adapter))    
    inTransaction {
      try {
        schema().create
      } catch {
        case e: Throwable => e.printStackTrace()
        throw e;
      }
    }
    LiftRules.liftRequest.append({
        case Req("console" ::_, _, _) => false
      })
  }

  def init = {
    initH2(() => DbSchema)    
  }
}

