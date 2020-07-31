package hr.meteorit.app.realmtest

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.realm.Realm
import io.realm.RealmResults
import io.realm.kotlin.where
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*

/**
 *
 *  PLAYGROUND ZA REALM
 *
 * Svrha ove aplikacije je pokazati neke načine kako se koristi realm, koje su njegove boljke i nedostaci
 * i na što sve treba obratiti pažnju.
 *
 * U onCreate() metodi su objašnjene osnovne stvari oko instanciranja realma i transakcija nakon čega slijedi 7 primjera.
 * Svaki primjer se pokreće preko botuna na layoutu i može se pratiti Logcat da se vidi što se događa s instancama kao i
 * profiler da se vidi što se događa s memorijom.
 *
 * test0 primjer pokazuje kako je zapravo zamišljeno od tvoraca Realma da se on koristi, međutim u kontekstu
 * data streamova (bilo rx ili coroutina) taj pattern nije od velike pomoći jer RealmResults nije moguće dijeliti među threadovima.
 * Zato su u ostalim primjerima patterni koji koriste asFlowable.
 *
 *
 *
 * P.S. - svi komentari su napisani zato da ih pročitaš :)
 *
 *
 *               ___________    ____
 *        ______/   \__//   \__/____\
 *      _/   \_/  :           //____\\
 *     /|      :  :  ..      /        \
 *    | |     ::     ::      \        /
 *    | |     :|     ||     \ \______/
 *    | |     ||     ||      |\  /  |
 *     \|     ||     ||      |   / | \
 *      |     ||     ||      |  / /_\ \
 *      | ___ || ___ ||      | /  /    \
 *       \_-_/  \_-_/ | ____ |/__/      \
 *                    _\_--_/    \      /
 *                   /____             /
 *                  /     \           /
 *                  \______\_________/
 *
 *                  Da, ti.
 *
 *
 */


class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        RealmKeks.init(this)                // Realm prije korištenja treba inicijalizirati
        // Pozivamo ga prije onCreate jer BaseActivity provjerava da li je realm inicijaliziran.
        // Npr. ako je realm kriptiran pa je potrebno obaviti login prije pokretanja nekog activitija.

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cleanDb()                           // for test purposes - čistim bazu ako je bilo neštu u njoj od prije

        // -- INSTANCE
        // Instanca se dobiva pozivom Realm.getDefaultInstance() što će vratiti jednu instancu PER THREAD
        // ali za svaki poziv te funkcije interni reference counter će se povećati za 1 te se instanca
        // TOG THREADa neće zatvoriti dok se ne pozove isti broj close() poziva.
        // Drugim riječima, da bi se oslobodili resursi za svaki getDefaultInstance() potreban je i jedan close() poziv.

        Log.i(TAG, "-- KREIRANJE INSTANCI --")
        val r = Realm.getDefaultInstance()  // prvo pozivanje
        Realm.getDefaultInstance()          // drugo pozivanje
        r.logInstanceCount()                // > 1 - jedan zato što su oba poziva na istom threadu
        r.logReferenceCount()               // > 2 - ali je zato reference count 2
        r.close()                           // prvo zatvaranje
        r.logInstanceCount()                // > 1 - i dalje jedan
        r.logReferenceCount()               // > 1 - ali se reference count smanjio za jedan
        r.close()                           // drugo zatvaranje
        r.logInstanceCount()                // > 0
        r.logReferenceCount()               // > 0

        Log.i(TAG, "-- KREIRANJE INSTANCI U KOTLINU--")
        // u kotlinu možemo pisati i ovako
        Realm.getDefaultInstance().apply {
            logInstanceCount()              // > 1
            // odradimo neke operacije s realmom i onda zatvorimo
            close()
            logInstanceCount()              // > 0
        }
        // ili ovako
        Realm.getDefaultInstance().use {
            it.logInstanceCount()           // > 1
            // use će automatski na kraju bloka pozvati close i također hendlati exceptione ako ih ima unutar bloka
        }
        r.logInstanceCount()                // > 0

        // Iako je meni osobno draži prvi način jer volim da su stvari eksplicitne i
        // podsjeća te uvijek na važnost zatvaranja instanci, "use" je bolji jer hendla exceptione iako ih nismo baš
        // vidjeli u praksi često.


        // -- TRANSAKCIJE
        // Slijede dva primjera kako će se izvoditi transakcije u ostalim primjerima.
        // Drugi primjer je isti kao prvi samo ga se poziva unutar coroutina da se uvjerimo da i tamo
        // sve štima s instancama realma.

        Log.i(TAG, "-- INSERT TRANSACTION--")
        insertTransaction()
        r.logInstanceCount()                // > 0

        Log.i(TAG, "-- SUSPENDED INSERT TRANSACTION--")
        runBlocking {
            suspendedInsertTransaction()
            r.logInstanceCount()            // > 0
        }

        /**
         * Slijedi nekoliko primjera na koji način se mogu observati podaci iz realma
         * dok se u pozadini upisuju podaci u bazu.
         * Svaka test funkcija ima opis i neka zapažanja, pa stisni F1 na svakoj.
         */
        test0.setOnClickListener { GlobalScope.launch { test0() } }
        test1.setOnClickListener { GlobalScope.launch { test1() } }
        test2.setOnClickListener { GlobalScope.launch { test2() } }
        test3.setOnClickListener { GlobalScope.launch { test3() } }
        test4.setOnClickListener { GlobalScope.launch { test4() } }

        /**
         * Summarum:
         * Test1 omogućuje sheranje objekata među threadovima, i iako dosta utječe na garbage collector u stvarnoj primjerni to
         * nije problem jer nemamo toliko puno transakcija odjednom.
         * Test2 način ne omogućuje sheranje rezultata među threadovima što je ok ako je to svijesna odluka.
         * Test3 i test4 koriste opciju freezanja rezultata no nije baš praktično za upotrebu jer se lako može dogoditi mem leak.
         * Test3 bi se mogao koristiti pod uvjetom da smo sigurni da observanje neće dugo trajati jer jede memoriju
         * sve dok ne prekinemo observanje podataka.
         * Test4 ima caku sa zatvaranjem freezanih instanci što nije osobito praktično.
         *
         * U svakom slučaju dobro pravilo je da instanca ne živi dugo na threadu.
         * Stvari poput insert, update i delete treba odraditi na bg threadu i odmah zatvoriti instancu.
         * Kod observanja podataka najbolje bi bilo koristiti standardni realmov pristup, no ako je potrebna
         * ikakva manipulacija nad podacima bolja praksa je by copy. Još jedna velika prednost bycopy je 100x lakši DEBUGGING
         * Podatke iz realm results nemoguće je vidjeti u Debug - Variables prozoru android studia.
         *
         * Za situacije kad trebamo jednokratno neki podatak svakako ga je nužno kopirati i zatvoriti instancu realma.
         *
         */

        /**
         * Dva bonus primjera
         */
        test5.setOnClickListener { GlobalScope.launch { test5() } }
        test6.setOnClickListener { GlobalScope.launch { test6() } }


        open_fragment.setOnClickListener { startFragment() }
        open_pager.setOnClickListener { startPager() }

    }

    /**
     * Ovo je primjer kako je Realm zapravo zamišljen da se koristi.
     * findAll() metoda vraća referencu na RealmResults.
     * RealmResults je samo pointer na set podataka.
     * Moguće je zakačiti changeListener na RealmResults koji će se pozvati kad god se promijeni nešto na tom setu podataka.
     * U tome je moć i brzina realma što se ne radi query nad podacima svaki put kad se nešto promijeni.
     */
    private suspend fun test0() {
        cleanDb()
        Log.i(TAG, "-- TEST0 -- on thread: ${Thread.currentThread().name}")
        RealmKeks.initWith(this, maxNumOfActiveVersions = 100, freezeObservable = false)

        var r: Realm? = null
        withContext(Dispatchers.Main) {
            r = Realm.getDefaultInstance()
            r!!.logInstanceCount() // > 1

            val results = r!!.where(SimpleRealmObject::class.java).findAll() // --------- results je referenca na set podataka i kao takva odmah se može predati npr u adapter
            results.addChangeListener { t: RealmResults<SimpleRealmObject> ->
                // ovaj t parametar je ISTI kao i results, to je ista referenca
                // To što znači da ga ne treba slati dalje npr u adapter nego je dovoljno adapteru reći notifyDatasetChanged()
                if (results == t) {
                    Log.i(TAG, "-- ISTI SU -- : len(results) = ${results.size}, len(t) = ${t.size}")
                }
            }
        }

        delay(500)
        make10BgTransactions()
        delay(500)
        r?.logInstanceCount() // > 1

        withContext(Dispatchers.Main) {
            r?.close()
            delay(500)
            r?.logInstanceCount() // > 0
        }
    }

    /**
     * Testiram observanje tijekom inserta 2 x 500 objekata metodom KOPIRANJA objekata iz realma
     * Efekti:
     * ✅ Sheranje rezultata po threadovima
     * ✅ Mala potrošnja memorije
     * ✅ Ne ostaju active versions baze
     * ❌ Puno garbage collectanja, iako u real world situacijama i nije tako strašno
     * ✅✅ Lakši debugging, RealmResults podatke je nemoguće vidjeti direktno u Debug - Variables prozoru
     */
    private suspend fun test1() {
        cleanDb()
        Log.i(TAG, "-- TEST1 -- on thread: ${Thread.currentThread().name}")
        RealmKeks.initWith(this, maxNumOfActiveVersions = 100, freezeObservable = false)
        val r = Realm.getDefaultInstance() // za potrebe logiranja
        r.close()
        val disposable = CompositeDisposable()

        r.logInstanceCount() // > 0

        RealmKeks.observeByCopy {
            it.where(SimpleRealmObject::class.java).findAll()
        }.observeOn(AndroidSchedulers.mainThread())
            .subscribe({
            }, {
                it.printStackTrace()
            }).also { disposable.add(it) }

        delay(500)
        make500BgTransactions()
        delay(500)
        r.logInstanceCount() // > 1
        make500BgTransactions()
        delay(500)
        r.logInstanceCount() // > 1

        Log.i(TAG, "-- DISPOSE OBSERVER --")
        disposable.clear()
        delay(500)
        r.logInstanceCount() // > 0
    }

    /**
     * Testiram observanje tijekom inserta 2 x 500 objekata metodom klasičnog observanja live rezultata iz realma
     * Efekti:
     * ❌ Sheranje rezultata po threadovima
     * ✅ Minimalna potrošnja memorije
     * ✅ Ne ostaju active versions baze
     * ✅ Nema garbage collectanja
     */
    private suspend fun test2() {
        cleanDb()
        Log.i(TAG, "-- TEST2 -- on thread: ${Thread.currentThread().name}")
        RealmKeks.initWith(this, maxNumOfActiveVersions = 50, freezeObservable = false)
        val r = Realm.getDefaultInstance() // za potrebe logiranja
        r.close()
        val disposable = CompositeDisposable()

        r.logInstanceCount() // > 0

        RealmKeks.observe {
            it.where(SimpleRealmObject::class.java).findAll()
        }.observeOn(AndroidSchedulers.mainThread())
            .subscribe({
            }, {
                it.printStackTrace()
            }).also { disposable.add(it) }

        delay(500)
        make500BgTransactions()
        delay(500)
        r.logInstanceCount() // > 1
        make500BgTransactions()
        delay(500)
        r.logInstanceCount() // > 1

        Log.i(TAG, "-- DISPOSE OBSERVER --")
        disposable.clear()
        delay(500)
        r.logInstanceCount() // > 0
    }

    /**
     * Testiram observanje tijekom inserta 2 x 500 objekata metodom freezanih objekata iz realma
     * Efekti:
     * ✅ Sheranje rezultata po threadovima
     * ❌ Potrošnja memorije raste sve dok se ne zatvore sve instance na tom threadu.
     * ❌ Generiraju se active versions baze sve dok se ne zatvori original instanca. Razlog ovoj i prethodnoj točki je što freeze
     * radi snapshot baze što je zapravo instanca za sebe koju isto treba zatvoriti; možemo je zatvoriti manualno ili će se zatvoriti
     * automatski kad se zatvori originalna instanca. test4 je primjer gdje zatvaramo freezane instance manualno.
     * ✅ Nema garbage collectanja
     */
    private suspend fun test3() {
        cleanDb()
        Log.i(TAG, "-- TEST3 -- on thread: ${Thread.currentThread().name}")
        RealmKeks.initWith(this, maxNumOfActiveVersions = 1100, freezeObservable = true)
        val r = Realm.getDefaultInstance() // za potrebe logiranja
        r.close()
        val disposable = CompositeDisposable()

        r.logInstanceCount() // > 1

        RealmKeks.observe {
            it.where<SimpleRealmObject>().findAll()
        }.observeOn(AndroidSchedulers.mainThread())
            .subscribe({
            }, {
                it.printStackTrace()
            }).also { disposable.add(it) }

        delay(500)
        make500BgTransactions()
        delay(500)
        r.logInstanceCount() // > ~500 !!
        make500BgTransactions()
        delay(500)
        r.logInstanceCount() // > ~1000 !!

        Log.i(TAG, "-- DISPOSE OBSERVER --")
        disposable.clear()
        delay(500)
        r.logInstanceCount() // > 0
    }

    /**
     * Testiram observanje tijekom inserta 2 x 500 objekata metodom freezanih objekata iz realma ali ih i zatvaram čim iskoristim podatke
     * Efekti:
     * ✅ Sheranje rezultata po threadovima
     * ✅ Minimalna potrošnja memorije.
     * ✅ Nema zaostalih active versions.
     * ✅ Nema garbage collectanja
     * ❌❌ Treba se sjetiti zatvoriti freezane instance tek nakon što iskoristim podatke. Ovo je također nezgodno u
     * situacijama kad vadim podatke za adapter, u tom slučaju morao bih neposredno prije updejtanja adaptera
     * closati realm instancu prethodnih podataka što je još jedna stvar za misliti i potencijalno napraviti bug.
     */
    private suspend fun test4() {
        cleanDb()
        Log.i(TAG, "-- TEST4 -- on thread: ${Thread.currentThread().name}")
        RealmKeks.initWith(this, maxNumOfActiveVersions = 1100, freezeObservable = true)
        val r = Realm.getDefaultInstance() // za potrebe logiranja
        r.close()
        val disposable = CompositeDisposable()

        r.logInstanceCount() // > 0

        RealmKeks.observe {
            it.where<SimpleRealmObject>().findAll()
        }.observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                it.realm.close() // <----------- ovo čini razliku između test3 i test4 primjera
                // nakon ovog poziva podaci više nisu dobavljivi tako da moram biti siguran da ih nakon ovoga više neću koristiti podatke
            }, {
                it.printStackTrace()
            }).also { disposable.add(it) }

        delay(500)
        make500BgTransactions()
        delay(500)
        r.logInstanceCount() // > 1
        make500BgTransactions()
        delay(500)
        r.logInstanceCount() // > 1

        Log.i(TAG, "-- DISPOSE OBSERVER --")
        disposable.clear()
        delay(500)
        r.logInstanceCount()
    }

    /**
     * Primjer u kojem se vidi da baš svaka instanca mora biti zatvorena na threadu prije nego se oslobode
     * resursi kad se koristi freeze. U stvarnosti nije rijetkost da imaš neku instancu koja dugo živi.
     */
    private suspend fun test5() {
        cleanDb()
        Log.i(TAG, "-- TEST5 -- on thread: ${Thread.currentThread().name}")
        val disposable = CompositeDisposable()
        RealmKeks.initWith(this, maxNumOfActiveVersions = 510, freezeObservable = true)

        var r: Realm? = null
        withContext(Dispatchers.Main) {
            r = Realm.getDefaultInstance()
        }
        r!!.logInstanceCount() // > 1

        RealmKeks.observe {
            it.where<SimpleRealmObject>().findAll()
        }.observeOn(AndroidSchedulers.mainThread())
            .subscribe({
            }, {
                it.printStackTrace()
            }).also { disposable.add(it) }

        delay(500)
        make500BgTransactions()
        delay(500)
        r!!.logInstanceCount() // > 503

        Log.i(TAG, "-- DISPOSE OBSERVER --")
        disposable.clear()
        delay(500)
        r!!.logInstanceCount() // > 503 !!

        withContext(Dispatchers.Main) {
            Log.i(TAG, "-- DISPOSE LONG LIVED -- on thread: ${Thread.currentThread().name}")
            r!!.close()
        }
        delay(500)
        r!!.logInstanceCount()
    }

    /**
     * Primjer kako korištenjem suspended metoda uopće ne možeš predvidjeti na kojem threadu će se nešto
     * izvršiti.
     */
    private suspend fun test6() {
        cleanDb()
        Log.i(TAG, "-- TEST5 -- on thread: ${Thread.currentThread().name}")
        val disposable = CompositeDisposable()
        RealmKeks.initWith(this, maxNumOfActiveVersions = 20, freezeObservable = false)

        val r = Realm.getDefaultInstance()
        r.logInstanceCount() // > 1

        RealmKeks.observe {
            it.where<SimpleRealmObject>().findAll()
        }.observeOn(AndroidSchedulers.mainThread())
            .subscribe({
            }, {
                it.printStackTrace()
            }).also { disposable.add(it) }

        Log.i(TAG, " on thread: ${Thread.currentThread().name}")
        delay(500)
        make500BgTransactions()
        Log.i(TAG, " on thread: ${Thread.currentThread().name}")
        delay(500)
        Log.i(TAG, " on thread: ${Thread.currentThread().name}")
        r.logInstanceCount() // > 2

        Log.i(TAG, "-- DISPOSE OBSERVER --")
        disposable.clear()
        delay(500)
        r.logInstanceCount() // > 1

        Log.i(TAG, "-- DISPOSE LONG LIVED -- on thread: ${Thread.currentThread().name}")
        r.close()    // > CRASH (ponekad) jer je varijabla incijalizirana na nekom drugom threadu iako je inicijaliziram unutar test6 funkcije
        delay(500)
        r.logInstanceCount()
    }

    private suspend fun make10BgTransactions() {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "-- MAKING 10 TRANSACTIONS on thread: ${Thread.currentThread().name}--")
            for (i in 0..10) {
                insertTransaction()
            }
        }
    }

    private suspend fun make500BgTransactions() {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "-- MAKING 500 TRANSACTIONS on thread: ${Thread.currentThread().name}--")
            for (i in 0..500) {
                insertTransaction()
            }
        }
    }

    private fun cleanDb() {
        Realm.getDefaultInstance().use {
            it.executeTransaction {
                it.deleteAll()
            }
            Log.i(TAG, "-- DB DELETE ALL --")
        }
    }

    private fun startFragment() {
        Log.d(TAG, "startFragment on thread: ${Thread.currentThread().name}")
        RealmKeks.initWith(this, maxNumOfActiveVersions = 100, freezeObservable = false)
        supportFragmentManager.beginTransaction().add(R.id.container, SimpleFragment.newInstance())
            .commit()
    }

    fun closeFragmentOrPager(f: Fragment) {
        Log.d(TAG, "closeFragmentOrPager on thread: ${Thread.currentThread().name}")
        if (view_pager.adapter?.count ?: 0 > 0) {
            view_pager.adapter = null
            view_pager.invalidate()
        } else {
            supportFragmentManager.beginTransaction().remove(f).commitNow()
        }
    }

    private fun startPager() {
        Log.d(TAG, "startPager on thread: ${Thread.currentThread().name}")
        RealmKeks.initWith(this, maxNumOfActiveVersions = 500, freezeObservable = false)
        view_pager.apply {
            adapter = MainPagerAdapter(this@MainActivity)
            offscreenPageLimit = 2
        }
    }

    companion object {

        /**
         * Ovo je isto kao i executeTransaction() samo što će se instanca otvoriti na drugom threadu.
         */
        suspend fun suspendedInsertTransaction() {
            withContext(Dispatchers.IO) {
                insertTransaction()
            }
        }

        /**
         * Neposredno prije transakcije dohvatimo instancu, izvršimo transakciju i zatvorimo instancu.
         */
        fun insertTransaction() {
            Log.d(TAG, "insertTransaction on thread: ${Thread.currentThread().name}")
            Realm.getDefaultInstance().apply {
                val nextID = where(SimpleRealmObject::class.java).max("id")?.let { it.toLong() + 1 } ?: 1
                executeTransaction {
                    it.insert(
                        SimpleRealmObject(
                            nextID,
                            "test $nextID"
                        )
                    )
                }
                close()
            }
        }
    }

}

fun Realm.logInstanceCount() {
    Log.i(TAG, "Total Instance Count: ${Realm.getGlobalInstanceCount(this.configuration)}")
}

fun Realm.logReferenceCount() {
    Log.i(TAG, "Total Reference Count: ${Realm.getLocalInstanceCount(this.configuration)}")
}

val Any.TAG: String
    get() = this.javaClass.simpleName + "@" + this.hashCode()