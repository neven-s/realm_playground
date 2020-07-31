package hr.meteorit.app.realmtest

import android.content.Context
import android.util.Log
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.rx.RealmObservableFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RealmKeks {

    fun init(context: Context) {
        Realm.init(context)
        val databaseName = "default.realm"
        val realmBuilder = RealmConfiguration.Builder()
            .name(databaseName)
            .schemaVersion(1)
            .deleteRealmIfMigrationNeeded()

        Realm.setDefaultConfiguration(realmBuilder.build())
    }

    fun initWith(context: Context, maxNumOfActiveVersions: Long, freezeObservable: Boolean) {
        Realm.init(context)
        val databaseName = "default.realm"
        val realmBuilder = RealmConfiguration.Builder()
            .name(databaseName)
            .schemaVersion(1)
            .deleteRealmIfMigrationNeeded()
            .maxNumberOfActiveVersions(maxNumOfActiveVersions)
            .rxFactory(RealmObservableFactory(freezeObservable))

        Realm.setDefaultConfiguration(realmBuilder.build())
    }

    fun observeByCopy(f: (Realm) -> RealmResults<out RealmObject>): Flowable<List<RealmObject>> {
        var realm: Realm? = null
        return Flowable.fromCallable {
            Log.i(TAG, "-- fromCallable on thread: ${Thread.currentThread().name} --")
            Realm.getDefaultInstance().apply { realm = this }
        }
            .subscribeOn(AndroidSchedulers.mainThread())
            .flatMap { f(it).asFlowable() }
            .map { it.realm.copyFromRealm(it) }
            .doFinally {
                GlobalScope.launch {
                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "-- CLOSE REALM --")
                        realm?.close()
                    }
                }
            }
    }

    fun <T> observe(f: (Realm) -> RealmResults<T>): Flowable<RealmResults<T>> {
        var realm: Realm? = null
        return Flowable.fromCallable {
            Log.i(TAG, "-- fromCallable on thread: ${Thread.currentThread().name} --")
            Realm.getDefaultInstance().apply { realm = this }
        }
            .subscribeOn(AndroidSchedulers.mainThread())
            .flatMap { f(it).asFlowable() }
            .doFinally {
                GlobalScope.launch {
                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "-- CLOSE REALM --")
                        realm?.close()
                    }
                }
            }
    }

    fun isInited(): Boolean {
        return try {
            Realm.getDefaultInstance().use {  }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "getDefaultInstance: ", t)
            false
        }
    }

}