package ru.asmr1.app.author

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import ru.asmr1.app.data.AuthorPageResponse

class AuthorPagerAdapter(
    activity: FragmentActivity,
    private val data: AuthorPageResponse
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AuthorHomeFragment.newInstance(data)
            1 -> AuthorVideosFragment.newInstance(data)
            2 -> AuthorPlaylistsFragment.newInstance(data)
            3 -> AuthorAboutFragment.newInstance(data)
            else -> Fragment()
        }
    }
}
