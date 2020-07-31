package hr.meteorit.app.realmtest

import android.util.Log
import androidx.lifecycle.ViewModel
import io.reactivex.Flowable
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SimpleViewModel : ViewModel() {
    /**
     * instancirati jednu instancu u Viewmodelu i zatvoriti je na onCleared
     * Koristiti tu instancu po potrebi unutar Viewmodela
     */
    private val realm = Realm.getDefaultInstance()

    // (kod inicijalizacije kotlin klase varijable i init blokovi se inicijaliziraju redom kako su napisani)
    init {
        Log.i(TAG, "-- INICIJALIZCIJA VIEW MODELA --")
        Log.d(TAG, "init SimpleViewModel on thread: ${Thread.currentThread().name}")
        realm.logInstanceCount() // > 1
    }

    override fun onCleared() {
        Log.i(TAG, "-- GAŠENJE VIEW MODELA --")
        Log.d(TAG, "onCleared on thread: ${Thread.currentThread().name}")
        super.onCleared()

        realm.close()
        realm.logInstanceCount() // > 2 - još se nije počistilo ali ako malo pričekamo...
        GlobalScope.launch {
            delay(200)
            Log.i(TAG, "-- NAKON 200ms --")
            realm.logInstanceCount() // >  0
        }
    }
    fun observeSimpleRealmObject(): Flowable<RealmResults<SimpleRealmObject>> {
        Log.d(TAG, "observeSimpleRealmObject on thread: ${Thread.currentThread().name}")

        realm.logInstanceCount() // > 1 - to je ista instanca kao ona gore u init bloku
        return realm.where(SimpleRealmObject::class.java)
            .findAll().asFlowable()
            .map {
                Log.d(TAG, "map on thread: ${Thread.currentThread().name}")
                // asFlowable će interno pozvati getDefaultInstance() i close() kad se disposa flowable
                // no nije mi jasno zašto je ovdje global instance count 2 jer se flowable poziva također na main threadu
                // Ajmo reći da je to još neka interna logika ali ne moramo brinuti o njoj, kad se dispoza ovaj flowable bit će sve ok
                realm.logInstanceCount() // > 2
                realm.logReferenceCount() // > 3
                it
            }
            .doFinally {
                Log.i(TAG, "observeSimpleRealmObject: do finally")
                // ovdje se još neće stići zatvoriti instanca kreirana sa asFlowable ali može se računati da će se zatvoriti eventualno
                Log.i(TAG, "logInstanceCount: ${Realm.getGlobalInstanceCount(realm.configuration)} - nije se stiglo zatvoriti") // > 2

                // vidi gore funkciju onCleared()
            }
    }
}