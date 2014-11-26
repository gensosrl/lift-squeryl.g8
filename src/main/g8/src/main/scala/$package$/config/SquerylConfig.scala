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
import org.squeryl.{Schema, Table}
import model._

object SquerylConfig extends Factory with Loggable {

  lazy val databaseProvider = $db_provider

  private def initH2(schema: () => Schema*) {
    Class.forName("org.h2.Driver")     
    import org.squeryl.adapters.H2Adapter
    import net.liftweb.squerylrecord.SquerylRecord
    import org.squeryl.Session
    SquerylRecord.initWithSquerylSession(Session.create(
      DriverManager.getConnection(Props.get("db.url", "jdbc:h2:mem:$db_name;DB_CLOSE_DELAY=-1"), Props.get("db.user", "$db_user"), Props.get("db.password", "$db_password")),
      new H2Adapter))    
    inTransaction {
      try {
        schema.map(s => s().create)
      } catch {
        case e: Throwable => e.printStackTrace()
        throw e;
      }
    }
    LiftRules.liftRequest.append({
        case Req("console" ::_, _, _) => false
      })
  }

  private def initMysql(schema: () => Schema*) {
    Class.forName("com.mysql.jdbc.Driver")     
    import org.squeryl.adapters.MySQLInnoDBAdapter
    import net.liftweb.squerylrecord.SquerylRecord
    import org.squeryl.Session
    SquerylRecord.initWithSquerylSession(Session.create(
      DriverManager.getConnection(Props.get("db.url", "jdbc:mysql://$db_host/$db_name"), Props.get("db.user", "$db_user"), Props.get("db.password", "$db_password")),
      new MySQLInnoDBAdapter))    
    inTransaction {
      try {
        schema.map(s => s().create)
      } catch {
        case e: Throwable => e.printStackTrace()
        throw e;
      }
    }
    LiftRules.liftRequest.append({
        case Req("console" ::_, _, _) => false
      })
  }

  private def initPostgreSql(schema: () => Schema*) {
    Class.forName("org.postgresql.Driver")    
    import org.squeryl.adapters.PostgreSqlAdapter
    import net.liftweb.squerylrecord.SquerylRecord
    import org.squeryl.Session
    SquerylRecord.initWithSquerylSession(Session.create(
      DriverManager.getConnection(Props.get("db.url", "jdbc:postgresql://$db_host/$db_name"), Props.get("db.user", "$db_user"), Props.get("db.password", "$db_password")),
      new PostgreSqlAdapter))    
    inTransaction {
      try {
        schema.map(s => s().create)
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
    databaseProvider match {
      case "h2" =>
        initH2(() => SquerylAuthSchema, () => DbSchema)    
      case "mysql" =>
        initMysql(() => SquerylAuthSchema, () => DbSchema)    
      case "postgresql" =>
        initPostgreSql(() => SquerylAuthchema, () => DbSchema)    
      case _ =>
        initH2(() => SquerylAuthSchema, () => DbSchema)    
    }
  }
}

