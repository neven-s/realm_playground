package hr.meteorit.app.realmtest

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("lifecycle-test","Main2Activity - onCreate")

        super.onCreate(savedInstanceState)
        if (!RealmKeks.isInited()) {
            finish()
        }
    }

    override fun onPause() {
        Log.d("lifecycle-test","Main2Activity - onPause")
        super.onPause()
    }

    override fun onDestroy() {
        Log.d("lifecycle-test","Main2Activity - onDestroy")
        super.onDestroy()
    }

    override fun onStart() {
        Log.d("lifecycle-test","Main2Activity - onStart")
        super.onStart()
        if (!RealmKeks.isInited()) {
            finish()
        }
    }

    override fun onStop() {
        Log.d("lifecycle-test","Main2Activity - onStop")
        super.onStop()
    }

    override fun onResume() {
        Log.d("lifecycle-test", "Main2Activity - onResume")
        super.onResume()
    }
}
