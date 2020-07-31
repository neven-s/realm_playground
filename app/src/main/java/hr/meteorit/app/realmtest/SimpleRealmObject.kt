package hr.meteorit.app.realmtest

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class SimpleRealmObject(
    @PrimaryKey
    var id: Long? = null,
    var name: String? = null
) : RealmObject()
