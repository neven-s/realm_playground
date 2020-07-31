package hr.meteorit.app.realmtest

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.blank_fragment.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SimpleFragment : Fragment(R.layout.blank_fragment) {

    companion object {
        fun newInstance() = SimpleFragment()
    }

    private lateinit var viewModel: SimpleViewModel
    private val cdisposable = CompositeDisposable()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(SimpleViewModel::class.java)

        btn_close.setOnClickListener {
            (activity as? MainActivity)?.closeFragmentOrPager(this)
        }
    }

    override fun onResume() {
        super.onResume()

        Log.i(TAG, "-- OBSERVING DATA --")
        viewModel.observeSimpleRealmObject()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.i(TAG, "-- GOT DATA --")
            }, {

            }).also { cdisposable.add(it) }

        GlobalScope.launch {
            for (i in 0..100) {
                MainActivity.insertTransaction()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        cdisposable.clear()
    }
}