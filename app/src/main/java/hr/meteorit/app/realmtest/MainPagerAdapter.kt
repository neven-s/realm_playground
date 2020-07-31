package hr.meteorit.app.realmtest

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter

class MainPagerAdapter(activity: MainActivity) : FragmentStatePagerAdapter(activity.supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getItem(position: Int): Fragment = when (position) {
        0 -> SimpleFragment()
        else -> SimpleFragment()
    }

    override fun getCount(): Int {
        return 2
    }
}