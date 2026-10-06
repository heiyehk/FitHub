package com.heiyehk.fithub.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.heiyehk.fithub.R
import com.heiyehk.fithub.ui.icons.FiBell
import com.heiyehk.fithub.ui.icons.FiCompass
import com.heiyehk.fithub.ui.icons.FiDevice
import com.heiyehk.fithub.ui.icons.FiUser

/** 四个主 Tab，发现与本机是两个主入口。 */
enum class AppTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Discover(R.string.tab_discover, FiCompass),
    Device(R.string.tab_device, FiDevice),
    Subscribe(R.string.tab_subscribe, FiBell),
    Profile(R.string.tab_profile, FiUser),
}
