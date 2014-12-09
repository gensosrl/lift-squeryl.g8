package $package$
package model


import org.joda.time.DateTime

import net.liftweb._
import common._
import http.{StringField => _, BooleanField => _, _}
import record.field._
import util.FieldContainer

import net.liftmodules.squerylauth._
import net.liftmodules.squerylauth.field._
import net.liftmodules.squerylauth.model._
import net.liftmodules.squerylauth.lib._
import net.liftweb.record.MetaRecord
import net.liftweb.util.Helpers._
import org.squeryl.Table
import net.liftweb.squerylrecord.RecordTypeMode._

object DbSchema extends AuthUserSchema[User] {
  val users: Table[User] = table("users")
  val rolesToUsers = manyToManyRelation(SquerylAuthSchema.roles, users, "role_user").via[RoleUser]((r,u,ru) => (ru.userId === u.id, r.id === ru.roleId))
}

